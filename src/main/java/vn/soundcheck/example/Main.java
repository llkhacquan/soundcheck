package vn.soundcheck.example;

import org.bytedeco.javacpp.*;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

import static org.bytedeco.javacpp.lept.pixDestroy;
import static org.bytedeco.javacpp.lept.pixRead;
import static org.bytedeco.javacpp.tesseract.PSM_SINGLE_BLOCK;
import static org.bytedeco.javacpp.tesseract.TessBaseAPI;

public class Main {

	private final static Logger LOG = LoggerFactory.getLogger(Main.class);
	private final static File resourcesFolder = new File("src/main/resources");
	private final static File tempDir = new File(System.getProperty("java.io.tmpdir"));

	public static void main(String args[]) {
		TessBaseAPI baseAPI = new TessBaseAPI();
		if (baseAPI.Init(".", "vie", 1) != 0) {
			System.err.println("Could not initialize tesseract.");
			System.exit(1);
		}
		OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
		baseAPI.SetPageSegMode(PSM_SINGLE_BLOCK);
		Arrays.stream(Objects.requireNonNull(resourcesFolder.listFiles((dir, name) -> name.endsWith(".jpg")))).forEach(f -> {
			// String absolutePath = new File(resourcesFolder, "1.jpg").getAbsolutePath();
			opencv_core.Mat imread = opencv_imgcodecs.imread(f.getAbsolutePath());
			opencv_core.CvArr cvArr = new opencv_core.CvMat(imread);
			opencv_highgui.cvvShowImage("origin - " + f.getName(), cvArr);

			lept.PIX image = pixRead(f.getAbsolutePath());
			BytePointer outputText;
			baseAPI.SetImage(image);
			int h = image.h();
			int w = image.w();
			{
				baseAPI.SetRectangle((int) (w * 0.1), (int) (h * 0.23), (int) (w * 0.8), (int) (h * 0.14));
				outputText = baseAPI.GetUTF8Text();
				System.out.println(outputText.getString());
				outputText.deallocate();

				baseAPI.SetRectangle((int) (w * 0.1), (int) (h * 0.38), (int) (w * 0.8), (int) (h * 0.05));
				outputText = baseAPI.GetUTF8Text();
				System.out.println(outputText.getString());
				outputText.deallocate();
			}

			pixDestroy(image);
		});
		baseAPI.End();
	}
}
