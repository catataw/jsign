package com.github.jsign.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

/**
 *
 * This class wraps sun.security.pkcs11.wrapper.PKCS11, so that we can access the native PKCS11 calls
 * directly.
 * A slot list and token labels for each slot is cached so that C_GetSlotList() only has to be called once and
 * so that C_GetTokenInfo() only has to be called once for each slot.
 * 
 * The {@link #getInstance(File)} method must be called before any PKCS#11 provider is created.
 *
 *  @version $Id$
 *
 */
public class PKCS11Wrapper {

    private final Method getSlotListMethod;
    private final Method getTokenInfoMethod;
    private final Field labelField;
    private final Object p11;
    private final HashMap<Long,String> slotListInfo;
    private final long slotList[];

    private PKCS11Wrapper(final String fileName) {
        Class<? extends Object> p11Class;
        try {
            p11Class = Class.forName("sun.security.pkcs11.wrapper.PKCS11");
        } catch (ClassNotFoundException e) {
            String msg = "Class sun.security.pkcs11.wrapper.PKCS11 was not found locally, could not wrap.";

            throw new IllegalStateException(msg, e);
        }
        
        try {
            this.getSlotListMethod = p11Class.getDeclaredMethod("C_GetSlotList", new Class[] { boolean.class });
        } catch (NoSuchMethodException e) {
            String msg = "Method C_GetSlotList was not found in class sun.security.pkcs11.wrapper.PKCS11, this may be due to"
                    + " a change in the underlying library.";

            throw new IllegalStateException(msg, e);
        } catch (SecurityException e) {
            String msg = "Access was denied to method sun.security.pkcs11.wrapper.PKCS11.C_GetSlotList";

            throw new IllegalStateException(msg, e);
        }
        try {
            this.getTokenInfoMethod = p11Class.getDeclaredMethod("C_GetTokenInfo", new Class[] { long.class });
        } catch (NoSuchMethodException e) {
            String msg = "Method C_GetTokenInfo was not found in class sun.security.pkcs11.wrapper.PKCS11, this may be due to"
                    + " a change in the underlying library.";
            throw new IllegalStateException(msg, e);
        } catch (SecurityException e) {
            String msg = "Access was denied to method sun.security.pkcs11.wrapper.PKCS11.C_GetTokenInfo";
            throw new IllegalStateException(msg, e);
        }
        try {
            this.labelField = Class.forName("sun.security.pkcs11.wrapper.CK_TOKEN_INFO").getField("label");
        } catch (NoSuchFieldException e) {
            String msg = "Field 'label' was not found in class sun.security.pkcs11.wrapper.CK_TOKEN_INFO, this may be due to"
                    + " a change in the underlying library.";
            throw new IllegalStateException(msg, e);
        } catch (SecurityException e) {
            String msg = "Access was denied to field sun.security.pkcs11.wrapper.CK_TOKEN_INFO.label";
            throw new IllegalStateException(msg, e);
        } catch (ClassNotFoundException e) {
            String msg = "Class sun.security.pkcs11.wrapper.CK_TOKEN_INFO was not found locally, could not wrap.";           
            throw new IllegalStateException(msg, e);
        }
        Method getInstanceMethod;
        try {
            getInstanceMethod = p11Class.getDeclaredMethod("getInstance", new Class[] { String.class, String.class, Class.forName("sun.security.pkcs11.wrapper.CK_C_INITIALIZE_ARGS"), boolean.class });
        } catch (NoSuchMethodException e) {
            String msg = "Method getInstance was not found in class sun.security.pkcs11.wrapper.PKCS11.CK_C_INITIALIZE_ARGS, this may be due to"
                    + " a change in the underlying library.";        
            throw new IllegalStateException(msg, e);
        } catch (SecurityException e) {
            String msg = "Access was denied to method sun.security.pkcs11.wrapper.CK_C_INITIALIZE_ARGS.getInstance";
            throw new IllegalStateException(msg, e);
        } catch (ClassNotFoundException e) {
            String msg = "Class sun.security.pkcs11.wrapper.CK_C_INITIALIZE_ARGS was not found locally, could not wrap.";
            throw new IllegalStateException(msg, e);
        }
        try {
            this.p11 = getInstanceMethod.invoke(null, new Object[] { fileName, "C_GetFunctionList", null, Boolean.FALSE });
        } catch (IllegalAccessException e) {
            String msg = "Method sun.security.pkcs11.wrapper.PKCS11.CK_C_INITIALIZE_ARGS.getInstance was not accessible, this may be due to"
                    + " a change in the underlying library.";            
            throw new IllegalStateException(msg, e);
        } catch (IllegalArgumentException e) {
            String msg = "Wrong arguments were passed to sun.security.pkcs11.wrapper.PKCS11.CK_C_INITIALIZE_ARGS.getInstance. This may be due to"
                    + " a change in the underlying library.";            
            throw new IllegalStateException(msg, e);
        } catch (InvocationTargetException e) {
            String msg = "Wrong arguments were passed to sun.security.pkcs11.wrapper.PKCS11.CK_C_INITIALIZE_ARGS.getInstance threw an exception "
                    + "for log.error(msg, e)";            
            throw new IllegalStateException(msg, e);
        }
        this.slotListInfo = new HashMap<Long,String>();
        this.slotList = C_GetSlotList();
        for( long id : this.slotList) {
            this.slotListInfo.put(Long.valueOf(id), getTokenLabelLocal(id));
        }
    }

    /**
     * Get an instance of the class. 
     * @param the p11 .so file.
     * @return the instance.
     * @throws IllegalArgumentException
     */
    public static PKCS11Wrapper getInstance(final File file) throws IllegalArgumentException {
        
    	final String canonicalFileName;
        
        try {
            canonicalFileName = file.getCanonicalPath();
        } 
        catch (IOException e) {
            throw new IllegalArgumentException(file+" is not a valid filename.",e );
        }

        return new PKCS11Wrapper(canonicalFileName);
    }

    /**
     * Get a list of p11 slot IDs to slots that has a token.
     * @return the list.
     */
    public long[] getSlotList() {
        return this.slotList;
    }

    /**
     * Get the token label of a specific slot ID.
     * @param slotID the ID of the slot
     * @return the token label, or null if no matching token was found.
     */
    public String getTokenLabel(long slotID) {
        return this.slotListInfo.get(Long.valueOf(slotID));
    }

    private long[] C_GetSlotList() {
        try {
            return (long[]) this.getSlotListMethod.invoke(this.p11, new Object[] { Boolean.TRUE });
        } catch (IllegalAccessException e) {
            String msg = "Access was denied to method sun.security.pkcs11.wrapper.PKCS11C.GetSlotList, this may be due to"
                    + " a change in the underlying library.";
            throw new IllegalStateException(msg, e);
        } catch (IllegalArgumentException e) {
            String msg = "Incorrect parameters sent to sun.security.pkcs11.wrapper.PKCS11C.GetSlotList, this may be due to"
                    + " a change in the underlying library.";
            throw new IllegalStateException(msg, e);
        } catch (InvocationTargetException e) {
            String msg = "Method sun.security.pkcs11.wrapper.PKCS11C.GetSlotList threw an unknown exception.";
          
            throw new IllegalStateException(msg, e);
        }
    }

    private String getTokenLabelLocal(long slotID)  {
        final Object tokenInfo;
        try {
            tokenInfo = this.getTokenInfoMethod.invoke(this.p11, new Object[] { Long.valueOf(slotID) });
        } catch (IllegalAccessException e) {
            String msg = "Access was denied to method sun.security.pkcs11.wrapper.PKCS11.C_GetTokenInfo, this may be due to"
                    + " a change in the underlying library.";
          
            throw new IllegalStateException(msg, e);
        } catch (IllegalArgumentException e) {
            String msg = "Incorrect parameters sent to sun.security.pkcs11.wrapper.PKCS11.C_GetTokenInfo, this may be due to"
                    + " a change in the underlying library.";
         
            throw new IllegalStateException(msg, e);
        } catch (InvocationTargetException e) {
            String msg = "Method sun.security.pkcs11.wrapper.PKCS11.C_GetTokenInfo threw an unknown exception.";
         
            throw new IllegalStateException(msg, e);
        }
        if (tokenInfo == null) {
            return null;
        }
        try {
            String result = String.copyValueOf((char[]) this.labelField.get(tokenInfo));
            return result.trim();
        } catch (IllegalArgumentException e) {
            String msg = "Field sun.security.pkcs11.wrapper.PKCS11.C_GetTokenInfo was not of type sun.security.pkcs11.wrapper.CK_TOKEN_INFO"
                    + ", this may be due to a change in the underlying library.";  
            throw new IllegalStateException(msg, e);
        } catch (IllegalAccessException e) {
            String msg = "Access was denied to field sun.security.pkcs11.wrapper.CK_TOKEN_INFO.label, this may be due to"
                    + " a change in the underlying library.";
   
            throw new IllegalStateException(msg, e);
        }
    }

	public HashMap<Long,String> getSlotListInfo() {
		return slotListInfo;
	}
}