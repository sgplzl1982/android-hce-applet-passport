package org.kevinvalk.hce.applet.passport;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;

import org.kevinvalk.hce.framework.Util;

public class Crypto
{
	
	/* Default IV */
	private static final IvParameterSpec ZERO_IV_PARAM_SPEC = new IvParameterSpec(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });
	
	/* Encrypt mode. */
	public static final int ENC_MODE = 1;
	
	/* MAC mode. */
	public static final int MAC_MODE = 2;
		
	/* Cache */
	static private Cipher cipherEngine = null;
	static private Mac macEngine = null;
	
	public static byte[] encrypt(byte[] buffer, SecretKey key)
	{
		try
    	{
			// Load cipher if not loaded
			if(cipherEngine == null)
				cipherEngine = Cipher.getInstance("DESede/CBC/NoPadding");
			
			cipherEngine.init(Cipher.ENCRYPT_MODE, key, ZERO_IV_PARAM_SPEC);
			return cipherEngine.doFinal(buffer);
    	}
		catch(Exception e)
		{
			throw new RuntimeException("Failed to encrypt buffer");
		}
	}
	
	public static byte[] decrypt(byte[] cipher, SecretKey key)
	{
		try
    	{
			// Load cipher if not loaded
			if(cipherEngine == null)
				cipherEngine = Cipher.getInstance("DESede/CBC/NoPadding");
			
			cipherEngine.init(Cipher.DECRYPT_MODE, key, ZERO_IV_PARAM_SPEC);
			return cipherEngine.doFinal(cipher);
    	}
		catch(Exception e)
		{
			throw new RuntimeException("Failed to encrypt buffer");
		}
	}

	public static boolean verifyMac(byte[] mac, byte[] buffer, SecretKey key)
	{
		byte[] mac2 = getMac(buffer, key);
		return verifyMac(mac, mac2);
	}
	
	public static boolean verifyMac(byte[] macOne, byte[] macTwo)
	{
		return Arrays.equals(macOne, macTwo);
	}
	
	public static byte[] getMac(byte[] cipher, SecretKey key)
	{
		try
    	{
			// Load mac if not loaded
			if (macEngine == null)
				macEngine = Mac.getInstance("ISO9797Alg3Mac", new org.spongycastle.jce.provider.BouncyCastleProvider());
			
			macEngine.init(key);
			return macEngine.doFinal(pad(cipher));
    	}
		catch(Exception e)
		{
			throw new RuntimeException("Failed to encrypt buffer");
		}
	}
	
	/**
	 * Pads the input <code>in</code> according to ISO9797-1 padding method 2.
	 *
	 * @param in input
	 *
	 * @return padded output
	 */
	public static byte[] pad(/*@ non_null */ byte[] in) {
		return pad(in, 0, in.length);
	}

	/*@ requires 0 <= offset && offset < length;
	  @ requires 0 <= length && length <= in.length;
	 */
	public static byte[] pad(/*@ non_null */ byte[] in,
			int offset, int length) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(in, offset, length);
		out.write((byte)0x80);
		while (out.size() % 8 != 0) {
			out.write((byte)0x00);
		}
		return out.toByteArray();
	}

	public static byte[] unpad(byte[] in) {
		int i = in.length - 1;
		while (i >= 0 && in[i] == 0x00) {
			i--;
		}
		if ((in[i] & 0xFF) != 0x80) {
			throw new IllegalStateException("unpad expected constant 0x80, found 0x" + Integer.toHexString((in[i] & 0x000000FF)) + "\nDEBUG: in = " + Util.toHex(in) + ", index = " + i);
		}
		byte[] out = new byte[i];
		System.arraycopy(in, 0, out, 0, i);
		return out;
	}

	/**
	 * Derives the ENC or MAC key from the keySeed.
	 *
	 * @param keySeed the key seed.
	 * @param mode either <code>ENC_MODE</code> or <code>MAC_MODE</code>.
	 * 
	 * @return the key.
	 */
	public static SecretKey deriveKey(byte[] keySeed, int mode)
	{
				try
		{
			MessageDigest shaDigest = MessageDigest.getInstance("SHA1");
			shaDigest.update(keySeed);
			byte[] c = { 0x00, 0x00, 0x00, (byte)mode };
			shaDigest.update(c);
			
			byte[] hash = shaDigest.digest();
			Util.d("CRYPTO", "Mode %d, Seed: %s, Key: %s\n", mode, Util.toHex(keySeed), Util.toHex(hash));
			byte[] key = new byte[24];
			System.arraycopy(hash, 0, key, 0, 8);
			System.arraycopy(hash, 8, key, 8, 8);
			System.arraycopy(hash, 0, key, 16, 8);
			SecretKeyFactory desKeyFactory = SecretKeyFactory.getInstance("DESede");
			return desKeyFactory.generateSecret(new DESedeKeySpec(key));
		}
		catch(Exception e)
		{
			throw new RuntimeException("Was not able to derive key");
		}
	}
	
	/**
	 * Computes the static key seed, based on information from the MRZ.
	 *
	 * @param documentNumber a string containing the document number.
	 * @param dateOfBirth a string containing the date of birth (YYMMDD).
	 * @param dateOfExpiry a string containing the date of expire (YYMMDD).
	 *
	 * @return a byte array of length 16 containing the key seed.
	 */
	public static byte[] computeKeySeed(String documentNumber, String dateOfBirth, String dateOfExpiry)
	{
		if (dateOfBirth.length() != 6 || dateOfExpiry.length() != 6)
			throw new RuntimeException("Wrong length MRZ input");

		/* Check digits... */
		byte[] cd1 = { (byte)Mrz.checkDigit(documentNumber) };
		byte[] cd2 = { (byte)Mrz.checkDigit(dateOfBirth) };
		byte[] cd3 = { (byte)Mrz.checkDigit(dateOfExpiry) };

		byte[] hash = null;
		MessageDigest shaDigest;
		try
		{
			shaDigest = MessageDigest.getInstance("SHA1");
			shaDigest.update(documentNumber.getBytes("UTF-8"));
			shaDigest.update(cd1);
			shaDigest.update(dateOfBirth.getBytes("UTF-8"));
			shaDigest.update(cd2);
			shaDigest.update(dateOfExpiry.getBytes("UTF-8"));
			shaDigest.update(cd3);
			hash = shaDigest.digest();
		}
		catch (Exception e)
		{
			throw new RuntimeException("Something wrong with SHA1 hashing");
		}

		byte[] keySeed = new byte[16];
		System.arraycopy(hash, 0, keySeed, 0, 16);
		return keySeed;
	}
}
