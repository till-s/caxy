package caxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.util.Properties;

/* Try to mimic what JCA is doing. However, we cannot
 *  - get resources built into JCA. This can only be remedied
 *    by copying such built-in resources to caxy and repackaging
 *    caxy.
 *  - know what 'Context' JCA is using. Hence, we introduce
 *    the '-J' option which lets the user set the name of
 *    the 'Context'.
 */
class CaxyJcaProp {

		protected String jcaCtxtName;

		protected static String jcaDfltCtxtName = "gov.aps.jca.Context";

		protected Properties _jcaProp;
		protected Properties _sysProp;
		protected Properties _usrProp;

		public CaxyJcaProp(String jcaCtxtName)
			throws IOException
		{
		InputStream is;
		String pathSep = System.getProperty( "file.separator" );
		String path    = null;

			this.jcaCtxtName = jcaCtxtName;

			_jcaProp = new Properties();
			_sysProp = new Properties(_jcaProp);
			_usrProp = new Properties(_sysProp);

			try {
				is = CaxyJcaProp.class.getResourceAsStream( "JCALibrary.properties" );
				if ( null == is ) {
					throw new RuntimeException("not found");
				}
				_jcaProp.load( is );

			} catch ( Throwable e ) {
				System.err.println("Unable to load built-in resources: " + e.getMessage());
			}

			try {
				path = System.getProperty( "java.home" ) + pathSep + "lib" + pathSep + "JCALibrary.properties";
				_sysProp.load( new FileInputStream( path ) );
			} catch ( Throwable e ) {
				/* silently ignore */
			}

			try {
				path = System.getProperty( "gov.aps.jca.JCALibrary.properties", null );
				if ( null == path ) {
					path = System.getProperty( "user.home" ) + pathSep + ".JCALibrary" + pathSep + "JCALibrary.properties";
				}
				_usrProp.load( new FileInputStream( path ) );
			} catch ( Throwable e ) {
			}
		}

		public String getJcaProperty(String key, String def)
		{
		String rval = null;

			if ( null != jcaCtxtName ) {
				rval = getProperty( new String( jcaCtxtName + "." +  key ), def );
			}

			if ( null == rval ) {
				rval = getProperty( new String( jcaDfltCtxtName + "." + key ), def );
			} 
			return rval;
		}

		public String getJcaProperty(String key)
		{
			return getJcaProperty(key, null);
		}

		public String getProperty(String key, String def)
		{
			return System.getProperty( key, _usrProp.getProperty( key, def ) );
		}

		public String getProperty(String key)
		{
			return this.getProperty( key, null );
		}

		int getJcaIntProperty(String key, int def)
		{
		String str;
			if ( (str = getJcaProperty(key)) != null ) {
				try {
					return Integer.parseInt(str);
				} catch ( java.lang.NumberFormatException e ) {
					System.err.println("Unable to parse "+key+" property");
					System.exit(1);
				}
			}
			return def;
		}
}
