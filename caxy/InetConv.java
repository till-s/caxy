package caxy;

import java.net.InetAddress;
import java.net.UnknownHostException;

class InetConv {
	public static int inet2int(InetAddress a)
	{
	byte [] ba   = a.getAddress();
	int     rval =   ((((int)ba[0]) & 0xff) << 24)
	               | ((((int)ba[1]) & 0xff) << 16)
	               | ((((int)ba[2]) & 0xff) <<  8)
	               | ((((int)ba[3]) & 0xff) <<  0);
		return rval;
	}

	public static InetAddress int2inet(int ia)
	{
	byte [] ba = new byte[4];
		return int2inet(ia, ba);
	}

	public static InetAddress int2inet(int ia, byte []ba)
	{
		ba[0] = (byte)((ia>>24) & 0xff);
		ba[1] = (byte)((ia>>16) & 0xff);
		ba[2] = (byte)((ia>> 8) & 0xff);
		ba[3] = (byte)((ia>> 0) & 0xff);
		try {
			return InetAddress.getByAddress(ba);
		} catch (UnknownHostException e) {
		}
		return null;
	}
}
