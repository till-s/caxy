package caxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/* Bidirectional channel with underlying socket */
public class PktBidChannel {

	protected ByteChannel bc;

	public static ServerSocketChannel createSrvChannel(InetSocketAddress sa, int backlog)
		throws IOException
	{
	ServerSocketChannel sc;
		sc = ServerSocketChannel.open();
		sc.socket().bind( sa, backlog );
		sc.socket().setReuseAddress( true );
		return sc;
	}
	
	public static ServerSocketChannel createSrvChannel(int port, int backlog)
			throws IOException {
		return createSrvChannel( new InetSocketAddress(port), backlog );
	}

	public static ServerSocketChannel createSrvChannel(boolean local_only, int port, int backlog)
			throws IOException {
		if ( local_only ) {
			byte[] lo = {127,0,0,1};
			return createSrvChannel( new InetSocketAddress( InetAddress.getByAddress(lo), port), backlog );
		} else {
			return createSrvChannel( port, backlog );
		}
	}

	public PktBidChannel(int port)
		throws IOException
	{
		InetSocketAddress sa = new InetSocketAddress( "localhost", port );
		bc = ProxifiedSocketChannel.open( sa );
	}

	public PktBidChannel(ServerSocketChannel sc)
		throws IOException
	{
		bc = sc.accept();
	}

	public PktInpChannel getPktInpChannel()
	{
		return new PktInpChannel((ReadableByteChannel)bc);
	}

	public PktOutChannel getPktOutChannel()
	{
		return new PktOutChannel((WritableByteChannel)bc);
	}
}
