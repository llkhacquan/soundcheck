import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {

	private final static Logger LOG = LoggerFactory.getLogger(Main.class);
	private final static File resourcesFolder = new File("src/main/resources");
	private final static File tempDir = new File(System.getProperty("java.io.tmpdir"));


	public static void main(String args[]) {
		Arrays.stream(Objects.requireNonNull(resourcesFolder.listFiles((dir, name) -> name.endsWith(".jpg")))).forEach(f -> {
			try {
				File temp = new File(tempDir, "hash." + String.valueOf(f.getAbsolutePath().hashCode()));
				String cmd = "tesseract " + f.getAbsolutePath() + " " + temp.getAbsolutePath() + " -l vie";
				execute(cmd);
				temp = new File(temp.getParent(), temp.getName() + ".txt");
				List<String> lines = Files.readAllLines(temp.toPath());
				List<String> collect = lines.stream().map(String::trim).filter(s -> s.length() > 0).collect(Collectors.toList());
				if (collect.size() == 0) {
					return;
				}
				System.out.println(f + " " + new String(Files.readAllBytes(temp.toPath())));
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		});
	}

	private static String execute(String cmd) throws InterruptedException, IOException {
		LOG.debug("Execute: " + cmd);
		Runtime run = Runtime.getRuntime();
		Process pr = run.exec(cmd);
		pr.waitFor();
		return "";
	}
}
