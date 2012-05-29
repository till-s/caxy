package caxy;

import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.ByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.DatagramChannel;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

class PktOutChannel {
	protected WritableByteChannel chnl;

	class IncompleteBufferWrittenException extends Exception {
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

/* Bidirectional channel with underlying socket */
class PktBidChannel {

	protected ByteChannel bc;

	public PktBidChannel(boolean inside, int port)
		throws IOException
	{
	ServerSocketChannel sc;
		if ( inside ) {
			InetSocketAddress sa = new InetSocketAddress( port );
			sc = ServerSocketChannel.open();
			sc.socket().bind( sa, 1 );
			sc.socket().setReuseAddress( true );
			bc = sc.accept();
		} else {
			InetSocketAddress sa = new InetSocketAddress( "localhost", port );
			bc = SocketChannel.open( sa );
		}
	}

	PktInpChannel getPktInpChannel()
	{
		return new PktInpChannel((ReadableByteChannel)bc);
	}

	PktOutChannel getPktOutChannel()
	{
		return new PktOutChannel((WritableByteChannel)bc);
	}
}

class PktInpChannel {
	protected ReadableByteChannel chnl;

	class IncompleteBufferReadException extends Exception {
		int got, wanted;
		protected IncompleteBufferReadException(int got_in, int wanted_in)
		{
			super( "Buffer not completely read (only " + got_in + " out of " + wanted_in + " bytes)" );
			got = got_in; wanted = wanted_in;
		}
	}

	public PktInpChannel(ReadableByteChannel chnl_in)
	{
		chnl = chnl_in;
	}

	public static PktInpChannel getStdin()
	{
		return new PktInpChannel( (new FileInputStream(java.io.FileDescriptor.in)).getChannel());
	}


	public void getPkt(ByteBuffer b)
		throws IOException, IncompleteBufferReadException
	{
		int here = b.position();
		int got;
		while ( b.remaining() > 0 && (got = chnl.read(b)) > 0 )
			/* nothing else to do */;
		if ( b.remaining() > 0 )
			throw new IncompleteBufferReadException(b.position() - here, b.limit());
	}
}
