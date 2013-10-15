package caxy;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/* Trivial class extending 'Thread' to copy
 * characters from an InputStream to an
 * OutputStream
 *
 * Typically used to copy stderr of a spawned
 * process (Runtime.exec()) to the VM stderr.
 */

class Errlog extends Thread {

	protected InputStream  is;
	protected OutputStream os;

	public Errlog(InputStream is)
	{
		this(is, System.err);
	}

	public Errlog(InputStream is, OutputStream os)
	{
		this.is = is;
		this.os = os;

		start( Errlog.class );
	}

	public void run()
	{
		byte []buf=new byte[1];
		try {
			while ( 1 == is.read(buf) ) {
				os.write(buf);
			}
		} catch (IOException e) {
			e.printStackTrace( System.err );
		} finally {
			System.err.println("Errlog: terminated");
		}
	}

	public void start(Class<? extends Errlog> c)
	{
		/* start thread only if this constructor is not called
		 * from a subclass (so that a subclass could do more
		 * initialization before calling start)
		 */
		if ( this.getClass().equals( c ) ) {
			super.start();
		}
	}

	/* Test code */
	public static void main(String []args)
		throws IOException
	{
		if ( args.length > 0 ) {
			Process   p = Runtime.getRuntime().exec(args);
			OutputStream s;
			new Errlog(p.getErrorStream());
			s=p.getOutputStream();
			s.write('H');
			s.write('E');
			s.write('L');
			s.write('O');
			s.write('\n');
			s.flush();
			System.err.println("FLUSHED");
		} else {
			new Errlog(System.in, System.err);
		}
	}
}
