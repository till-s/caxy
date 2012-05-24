package caxy;

import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.ByteChannel;
import java.nio.channels.DatagramChannel;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;

class PktChannel {
	protected ByteChannel chnl;

	class IncompleteBufferReadException extends Exception {
		int got, wanted;
		protected IncompleteBufferReadException(int got_in, int wanted_in)
		{
			got = got_in; wanted = wanted_in;
		}
	}

	class IncompleteBufferWrittenException extends Exception {
		int put, wanted;
		protected IncompleteBufferWrittenException(int put_in, int wanted_in)
		{
			put = put_in; wanted = wanted_in;
		}
	}


	public PktChannel(ByteChannel chnl_in)
	{
		chnl = chnl_in;
	}

	public static PktChannel getStdout()
	{
		return new PktChannel( (new FileOutputStream(java.io.FileDescriptor.out)).getChannel());
	}

	public static PktChannel getStdin()
	{
		return new PktChannel( (new FileInputStream(java.io.FileDescriptor.in)).getChannel());
	}


	public void getPkt(ByteBuffer b)
		throws IOException, IncompleteBufferReadException
	{
		int here = b.position();
		while ( b.remaining() > 0 && chnl.read(b) > 0 )
			;
		if ( b.remaining() > 0 )
			throw new IncompleteBufferReadException(b.position() - here, b.limit());
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

	public static PktChannel open(boolean inside, int port)
		throws IOException
	{
	ByteChannel bc;
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
		
		return new PktChannel( bc );
	}
}
