import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import de.tobiasroeser.lambdatest.junit.FreeSpec;

import static de.tobiasroeser.lambdatest.Expect.*;

public class Test extends FreeSpec {

	public Test() {

		test("ClassToWeave.print should also print aspects", () -> {
			final PrintStream origOut = System.out;
			final ByteArrayOutputStream cacheOut = new ByteArrayOutputStream();
			final PrintStream testOut = new PrintStream(cacheOut);
			System.setOut(testOut);
			try {
				new ClassToWeave().print();
				expectString(cacheOut.toString())
						.contains("Weave me")
						.contains("Trace")
						.contains("before print()");

			} finally {
				expectEquals(System.out, testOut, "System.out is no longer the same");
				System.setOut(origOut);
				origOut.print(cacheOut.toString());
			}
		});


	}

}
