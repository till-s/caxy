package caxy;

import java.net.InetSocketAddress;

class WireIp4Addr {

	protected int addr;
	protected int port;

	protected InetSocketAddress sa;

	WireIp4Addr(int addr_in, int port_in)
	{
		addr = addr_in; port = port_in;
		sa   = null;
	}

	WireIp4Addr(InetSocketAddress sa_in)
	{
		sa = sa_in;
		
	}

	public synchronized void getAddr(int []a)
	{
		a[0] = addr;
		a[1] = port;
	}

	public synchronized InetSocketAddress get_sa()
	{
		if ( null == sa ) {
			sa = new InetSocketAddress(InetConv.int2inet(addr), port);
		}
		return sa;
	}

	public synchronized void setAddr(int addr_in, int port_in)
	{
		addr = addr_in; port = port_in;
		sa   = null;
	}

}
