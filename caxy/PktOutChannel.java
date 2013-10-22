package caxy;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.DatagramChannel;
import java.nio.channels.WritableByteChannel;

public class PktOutChannel {
	protected WritableByteChannel chnl;

	class IncompleteBufferWrittenException extends Exception {
		private static final long serialVersionUID = 3662593742711295725L;
		int put, wanted;
		protected IncompleteBufferWrittenException(int put_in, int wanted_in)
		{
			super( "Buffer not completely written (only " + put_in + " out of " + wanted_in + " bytes)" );
			put = put_in; wanted = wanted_in;
		}
	}

	public PktOutChannel(WritableByteChannel chnl_in)
	{
		chnl  = chnl_in;
	}

	public void close()
		throws IOException
	{
		chnl.close();
	}

	// I get a warning:
	//   "Resource leak: '<unassigned Closeable value>' is never closed"
	// However, this seems to be bogus since the channel returned by getChannel()
	// actually *is* closeable (and there is a run-time error if the FileOutputStream
	// is closed here).
	@SuppressWarnings("resource")
	public static PktOutChannel getStdout()
	{
		return new PktOutChannel( (new FileOutputStream(java.io.FileDescriptor.out)).getChannel());
	}

	protected void putBuf(ByteBuffer b, SocketAddress peer)
		throws IOException, IncompleteBufferWrittenException
	{
		int here = b.position();

		while ( b.remaining() > 0 && (peer == null ? chnl.write(b) : ((DatagramChannel)chnl).send(b, peer)) > 0 )
			;
		if ( b.remaining() > 0 )
			throw new IncompleteBufferWrittenException(b.position() - here, b.limit());

	}

	protected synchronized void putPkt(WrapHdr wHdr, SocketAddress peer)
		throws IOException, IncompleteBufferWrittenException
	{
		wHdr.out(this, peer);
	}

	protected synchronized void putPkt(WrapHdr wHdr, ByteBuffer pld, SocketAddress peer)
		throws IOException, IncompleteBufferWrittenException
	{

		wHdr.out(this, peer);
		pld.rewind();
		putBuf(pld, peer);
	}

	protected synchronized void putPkt(WrapHdr wHdr, CaPkt caPkt, SocketAddress peer)
		throws IOException, IncompleteBufferWrittenException
	{
		wHdr.out(this, peer);
		caPkt.out(this, peer);
	}
}

/* Subclass of PktOutChannel which makes sure the underlying
 * OutputStream is flushed after writing packets.
 * This seems to be necessary when we write to a sub-process'es
 * stdin obtained via Process.getOutputStream().
 *
 * Very ugly!
 *
 */
class PktOutStrmChannel extends PktOutChannel {

	protected OutputStream        ostrm;

	public PktOutStrmChannel(OutputStream ostrm)
	{
		super( Channels.newChannel( ostrm ) );
		this.ostrm = ostrm;
	}

	protected void putBuf(ByteBuffer b, SocketAddress peer)
		throws IOException, IncompleteBufferWrittenException
	{
		super.putBuf(b, peer);
		{
			/* Ugly hack: If I stitch together a pipe from the outside to the inside
			 * using the OutputStream/InputStreams from Process (when spawning a new
			 * process) then the communication hangs -- unless I flush the output stream
			 * after we send :-(. Unfortunately there is no way to flush
			 * a WritableByteChannel -- hence we can't do the flushing just using the
			 * channel.
			 * Thus, we keep a handle to the output stream around and flush manually
			 * here. How ugly!
			 * 
			 * Note that this is not necessary for any other kind of stream:
			 *   - TCP connection
			 *   - FileInputStream/FileOutputStream via getStin
			 */
		}
		ostrm.flush();
	}
}