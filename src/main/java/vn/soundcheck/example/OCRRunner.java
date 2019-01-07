package vn.soundcheck.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import org.apache.commons.io.FilenameUtils;
import org.bytedeco.javacpp.*;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static org.bytedeco.javacpp.lept.pixDestroy;
import static org.bytedeco.javacpp.tesseract.*;

/**
 * for each images in resourcesFolder:
 * <ol>
 * <li>detect boxes</li>
 * <li>if boxes exists, try to perform OCR on those boxes and </li>
 * </ol>
 */
public final class OCRRunner {

	private final static Logger LOG = LoggerFactory.getLogger(OCRRunner.class);
	private final static File resourcesFolder = new File("src/main/resources");
	private final static File tempDir = new File(System.getProperty("java.io.tmpdir"));

	public static void main(String[] args) throws InterruptedException, FileNotFoundException, JsonProcessingException {
		TessBaseAPI baseAPI = new TessBaseAPI();
		if (baseAPI.Init(".", "vie", 1) != 0) {
			System.err.println("Could not initialize tesseract.");
			System.exit(1);
		}
		File outDir = new File("ocr-out");
		outDir.mkdirs();
		Preconditions.checkArgument(outDir.isDirectory());
		OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
		File[] files = Objects.requireNonNull(resourcesFolder.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png")));
		for (File f : files) {
			PriorityQueue<Gap> queue = detectBoxes(f);
			if (queue == null) {
				LOG.info(f.getName() + " does not have boxes");
				continue;
			}
			lept.PIX image = lept.pixRead(f.getAbsolutePath());
			int w = image.w();
			baseAPI.SetImage(image);
			baseAPI.SetSourceResolution((int) Math.sqrt(image.h() * image.w() / 2.3 / 4.1));
			List<Gap> gapList = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				gapList.add(queue.poll());
			}
			gapList.sort(Comparator.comparingInt(o -> o.start));
			String[] questions = new String[4];
			File outputFile = new File(outDir, FilenameUtils.removeExtension(f.getName()) + ".ocr");
			for (int i = 0; i < 4; i++) {
				Gap gap = gapList.get(i);
				opencv_core.Rect rect;
				if (i == 0) {
					rect = new opencv_core.Rect((int) (w * 0.1), gap.start + 5, (int) (w * 0.8), gap.end - gap.start - 5);
				} else {
					rect = new opencv_core.Rect((int) (w * 0.2), gap.start + 5, (int) (w * 0.6), gap.end - gap.start - 5);
				}
				questions[i] = extractText(baseAPI, rect, i == 0).replaceAll("\n", " ");
				System.out.println(f.getAbsolutePath());
				System.out.println("\t" + questions[i]);
				rect.close();
			}
			try (PrintWriter out = new PrintWriter(outputFile)) {
				OCRResult re = new OCRResult(f.getName(), questions[0], questions[1], questions[2], questions[3]);
				out.write(toJsonString(re));
			}
			pixDestroy(image);
		}
		baseAPI.End();
		baseAPI.close();
	}

	private static PriorityQueue<Gap> detectBoxes(File f) {
		final opencv_core.Mat originBW = opencv_imgcodecs.imread(f.getAbsolutePath(), opencv_imgcodecs.IMREAD_GRAYSCALE);
		final int w = originBW.cols();
		final int h = originBW.rows();
		opencv_core.Mat gray = new opencv_core.Mat();
		opencv_core.Mat bw = new opencv_core.Mat();

		// remove noise
		opencv_core.bitwise_not(originBW, gray);
		opencv_imgproc.adaptiveThreshold(gray, bw, 255, opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, opencv_imgproc.THRESH_BINARY, 13, -2);

		opencv_core.Mat horizontal = bw.clone();
		{
			// detect edges of answer-boxes rectangles
			final int estimatedBoxWidth = (int) (horizontal.cols() * 0.35);
			opencv_core.Mat horizontalStructure = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new opencv_core.Size(estimatedBoxWidth, 1));
			// remove noise
			opencv_imgproc.erode(horizontal, horizontal, horizontalStructure);
			opencv_imgproc.dilate(horizontal, horizontal, horizontalStructure);
		}

		// detect coordinates of answer-boxes
		opencv_core.Mat centerCol = horizontal.col(w / 2);
		PriorityQueue<Gap> queue = new PriorityQueue<>();
		int lastI = -1;
		for (int i = h / 10; i < h * 0.9 - 1; i++) {
			if (centerCol.ptr(i, 0).get() == 0 && centerCol.ptr(i + 1, 0).get() == -1) {
				if (lastI > 0) {
					queue.add(new Gap(lastI, i));
				}
				lastI = i;
			}
		}
//		originFrame.showImage(converter.convert(originBW));
//		originFrame.waitKey();
		horizontal.close();
		originBW.close();
		return queue.size() < 5 ? null : queue;
	}

	private static String extractText(TessBaseAPI baseAPI, opencv_core.Rect roi, boolean blockText) {
		baseAPI.SetRectangle(roi.x(), roi.y(), roi.width(), roi.height());
		if (blockText) {
			baseAPI.SetPageSegMode(PSM_SINGLE_BLOCK);
		} else {
			baseAPI.SetPageSegMode(PSM_SINGLE_LINE);
		}
		BytePointer outputText;
		outputText = baseAPI.GetUTF8Text();
		String result = outputText.getString();
		outputText.deallocate();
		return result;
	}

	public static String toJsonString(Object o) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(FAIL_ON_EMPTY_BEANS, false);
		return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
	}

	private static class Gap implements Comparable<Gap> {
		private final int start;
		private final int end;

		private Gap(int start, int end) {
			Preconditions.checkArgument(start <= end);
			this.start = start;
			this.end = end;
		}

		@Override
		public int compareTo(Gap o) {
			Preconditions.checkNotNull(o);
			return -Integer.compare(this.end - this.start, o.end - o.start);
		}
	}

	private static class OCRResult {
		// file name
		public final String id;
		public final String q0;
		public final String q1;
		public final String q2;
		public final String q3;

		private OCRResult(String id, String q0, String q1, String q2, String q3) {
			this.id = id;
			this.q0 = q0;
			this.q1 = q1;
			this.q2 = q2;
			this.q3 = q3;
		}
	}
}
