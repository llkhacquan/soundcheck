package vn.soundcheck.example;

import com.google.common.base.Preconditions;
import org.bytedeco.javacpp.*;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static org.bytedeco.javacpp.lept.pixDestroy;
import static org.bytedeco.javacpp.tesseract.*;

public class Main {

	private final static Logger LOG = LoggerFactory.getLogger(Main.class);
	private final static File resourcesFolder = new File("src/main/resources");
	private final static File tempDir = new File(System.getProperty("java.io.tmpdir"));

	public static void main(String args[]) throws InterruptedException {
		TessBaseAPI baseAPI = new TessBaseAPI();
		if (baseAPI.Init(".", "vie", 1) != 0) {
			System.err.println("Could not initialize tesseract.");
			System.exit(1);
		}
		OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
		CanvasFrame originFrame = new CanvasFrame("Origin");
		File[] files = Objects.requireNonNull(resourcesFolder.listFiles((dir, name) -> name.endsWith(".jpg")));
		for (File f : files) {
			// String absolutePath = new File(resourcesFolder, "1.jpg").getAbsolutePath();
			opencv_core.Mat originBW = opencv_imgcodecs.imread(f.getAbsolutePath(), opencv_imgcodecs.IMREAD_GRAYSCALE);
			int w = originBW.cols();
			int h = originBW.rows();
			opencv_core.Mat gray = new opencv_core.Mat();
			opencv_core.Mat bw = new opencv_core.Mat();
			opencv_core.bitwise_not(originBW, gray);
			opencv_imgproc.adaptiveThreshold(gray, bw, 255, opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, opencv_imgproc.THRESH_BINARY, 13, -2);
			opencv_core.Mat horizontal = bw.clone();
			{
				int horizontal_size = (int) (horizontal.cols() * 0.35);
				opencv_core.Mat horizontalStructure = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new opencv_core.Size(horizontal_size, 1));
				opencv_imgproc.erode(horizontal, horizontal, horizontalStructure);
				opencv_imgproc.dilate(horizontal, horizontal, horizontalStructure);
			}
			opencv_core.Mat centerCol = horizontal.col(w / 2);

			PriorityQueue<Gap> queue = new PriorityQueue<>();
			int lastI = -1;
			for (int i = h / 10; i < h * 0.8 - 1; i++) {
				if (centerCol.ptr(i, 0).get() == 0 && centerCol.ptr(i + 1, 0).get() == -1) {
					if (lastI > 0) {
						queue.add(new Gap(lastI, i));
					}
					lastI = i;
				}
			}
			if (queue.size() < 5) {
				LOG.info("Skip this file {}", f);
				continue;
			}

			lept.PIX image = lept.pixRead(f.getAbsolutePath());
			baseAPI.SetImage(image);
			List<Gap> gapList = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				gapList.add(queue.poll());
			}
			gapList.sort(Comparator.comparingInt(o -> o.start));
			for (int i = 0; i < 4; i++) {
				Gap gap = gapList.get(i);
				opencv_core.Rect rect;
				if (i == 0) {
					rect = new opencv_core.Rect((int) (w * 0.1), gap.start + 5, (int) (w * 0.8), gap.end - gap.start - 5);
				} else {
					rect = new opencv_core.Rect((int) (w * 0.2), gap.start + 5, (int) (w * 0.6), gap.end - gap.start - 5);
				}
				opencv_imgproc.rectangle(originBW, rect, new opencv_core.Scalar(-1, -1, -1, 0), 2, 0, 0);
				String text = extractText(baseAPI, rect, i == 0);
				System.out.println(text);
			}
			pixDestroy(image);
			originFrame.showImage(converter.convert(originBW));
			originFrame.waitKey();
		}
		originFrame.dispose();
		baseAPI.End();
		baseAPI.close();
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
}
