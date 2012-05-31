package caxy;

public interface CaxyConst {
	public static final int  CATUN_VERSION_1    = 0x10;
	public static final int  CATUN_VERSION_2    = 0x20;
	public static final int  CATUN_MAJOR_MSK    = 0xf0;

	public static final int  CA_PORT_BASE       = 5056;

	public static final int  CA_MAJOR_PROTO_REV = 4;
	public static final int  CA_MINOR_PROTO_REV = 11;

	public static final int  INADDR_ANY         = 0;
	public static final int  INADDR_LOOPBACK    = 0x7f000001;
	public static final int  INADDR_NONE        = 0xffffffff;

	public static final int  CA_SERVER_PORT     = CA_PORT_BASE + 2 * CA_MAJOR_PROTO_REV;
	public static final int  CA_REPEATER_PORT   = CA_PORT_BASE + 2 * CA_MAJOR_PROTO_REV + 1;

	public static final int  DEBUG_UDP          = 1;
	public static final int  DEBUG_TCP          = 2;
	public static final int  DEBUG_LUP          = 4;
	public static final int  DEBUG_NOB          = 8;
	// Debug flags >= 1<<16 are reserved for class-specific debugging
	public static final int  DEBUG_RAWBUF       = (1<<16); /* WrapHdr     */
	public static final int  DEBUG_PROPS        = (1<<17); /* CaxyJcaProp */
	public static final int  DEBUG_ALIST        = (1<<18); /* CaxyJcaProp */
}
