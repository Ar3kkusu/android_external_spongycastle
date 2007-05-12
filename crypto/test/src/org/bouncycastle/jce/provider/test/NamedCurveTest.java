package org.bouncycastle.jce.provider.test;

import org.bouncycastle.asn1.nist.NISTNamedCurves;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.teletrust.TeleTrusTNamedCurves;
import org.bouncycastle.asn1.x9.X962NamedCurves;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.util.test.SimpleTest;

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Enumeration;
import java.util.Hashtable;

public class NamedCurveTest
    extends SimpleTest
{
    private static Hashtable CURVE_NAMES = new Hashtable();
    
    static
    {
        CURVE_NAMES.put("prime192v1", "prime192v1"); // X9.62
        CURVE_NAMES.put("sect571r1", "sect571r1"); // sec
        CURVE_NAMES.put("secp224r1", "secp224r1");
        CURVE_NAMES.put("B-409", SECNamedCurves.getName(NISTNamedCurves.getOID("B-409")));   // nist
        CURVE_NAMES.put("P-521", SECNamedCurves.getName(NISTNamedCurves.getOID("P-521")));
        CURVE_NAMES.put("brainpoolP512r1", "brainpoolP512r1");         // TeleTrusT
    }
    
    public void testCurve(
        String name)
        throws Exception
    {
        ECGenParameterSpec     ecSpec = new ECGenParameterSpec(name);

        if (ecSpec == null)
        {
            fail("no curve for " + name + " found.");
        }

        KeyPairGenerator    g = KeyPairGenerator.getInstance("ECDH", "BC");

        g.initialize(ecSpec, new SecureRandom());

        //
        // a side
        //
        KeyPair aKeyPair = g.generateKeyPair();

        KeyAgreement aKeyAgree = KeyAgreement.getInstance("ECDHC", "BC");

        aKeyAgree.init(aKeyPair.getPrivate());

        //
        // b side
        //
        KeyPair bKeyPair = g.generateKeyPair();

        KeyAgreement bKeyAgree = KeyAgreement.getInstance("ECDHC", "BC");

        bKeyAgree.init(bKeyPair.getPrivate());

        //
        // agreement
        //
        aKeyAgree.doPhase(bKeyPair.getPublic(), true);
        bKeyAgree.doPhase(aKeyPair.getPublic(), true);

        BigInteger  k1 = new BigInteger(aKeyAgree.generateSecret());
        BigInteger  k2 = new BigInteger(bKeyAgree.generateSecret());

        if (!k1.equals(k2))
        {
            fail("2-way test failed");
        }

        //
        // public key encoding test
        //
        byte[]              pubEnc = aKeyPair.getPublic().getEncoded();
        KeyFactory          keyFac = KeyFactory.getInstance("ECDH", "BC");
        X509EncodedKeySpec  pubX509 = new X509EncodedKeySpec(pubEnc);
        ECPublicKey         pubKey = (ECPublicKey)keyFac.generatePublic(pubX509);

        if (!pubKey.getW().equals(((ECPublicKey)aKeyPair.getPublic()).getW()))
        {
            fail("public key encoding (Q test) failed");
        }

        if (!(pubKey.getParams() instanceof ECNamedCurveSpec))
        {
            fail("public key encoding not named curve");
        }

        //
        // private key encoding test
        //
        byte[]              privEnc = aKeyPair.getPrivate().getEncoded();
        PKCS8EncodedKeySpec privPKCS8 = new PKCS8EncodedKeySpec(privEnc);
        ECPrivateKey        privKey = (ECPrivateKey)keyFac.generatePrivate(privPKCS8);

        if (!privKey.getS().equals(((ECPrivateKey)aKeyPair.getPrivate()).getS()))
        {
            fail("private key encoding (S test) failed");
        }

        if (!(privKey.getParams() instanceof ECNamedCurveSpec))
        {
            fail("private key encoding not named curve");
        }

        ECNamedCurveSpec privSpec = (ECNamedCurveSpec)privKey.getParams();
        if (!(privSpec.getName().equals(name) || privSpec.getName().equals(CURVE_NAMES.get(name))))
        {
            fail("private key encoding wrong named curve. Expected: " + CURVE_NAMES.get(name) + " got " + privSpec.getName());
        }
    }

    public void testECDSA(
        String name)
        throws Exception
    {
        ECGenParameterSpec     ecSpec = new ECGenParameterSpec(name);
 
        if (ecSpec == null)
        {
            fail("no curve for " + name + " found.");
        }

        KeyPairGenerator    g = KeyPairGenerator.getInstance("ECDSA", "BC");

        g.initialize(ecSpec, new SecureRandom());

        Signature sgr = Signature.getInstance("ECDSA", "BC");
        KeyPair   pair = g.generateKeyPair();
        PrivateKey sKey = pair.getPrivate();
        PublicKey vKey = pair.getPublic();

        sgr.initSign(sKey);

        byte[] message = new byte[] { (byte)'a', (byte)'b', (byte)'c' };

        sgr.update(message);

        byte[]  sigBytes = sgr.sign();

        sgr.initVerify(vKey);

        sgr.update(message);

        if (!sgr.verify(sigBytes))
        {
            fail(name + " verification failed");
        }

        //
        // public key encoding test
        //
        byte[]              pubEnc = vKey.getEncoded();
        KeyFactory          keyFac = KeyFactory.getInstance("ECDH", "BC");
        X509EncodedKeySpec  pubX509 = new X509EncodedKeySpec(pubEnc);
        ECPublicKey         pubKey = (ECPublicKey)keyFac.generatePublic(pubX509);

        if (!pubKey.getW().equals(((ECPublicKey)vKey).getW()))
        {
            fail("public key encoding (Q test) failed");
        }

        if (!(pubKey.getParams() instanceof ECNamedCurveSpec))
        {
            fail("public key encoding not named curve");
        }

        //
        // private key encoding test
        //
        byte[]              privEnc = sKey.getEncoded();
        PKCS8EncodedKeySpec privPKCS8 = new PKCS8EncodedKeySpec(privEnc);
        ECPrivateKey        privKey = (ECPrivateKey)keyFac.generatePrivate(privPKCS8);

        if (!privKey.getS().equals(((ECPrivateKey)sKey).getS()))
        {
            fail("private key encoding (S test) failed");
        }

        if (!(privKey.getParams() instanceof ECNamedCurveSpec))
        {
            fail("private key encoding not named curve");
        }

        ECNamedCurveSpec privSpec = (ECNamedCurveSpec)privKey.getParams();
        if (!privSpec.getName().equalsIgnoreCase(name))
        {
            fail("private key encoding wrong named curve. Expected: " + name + " got " + privSpec.getName());
        }
    }

    public String getName()
    {
        return "NamedCurve";
    }
    
    public void performTest()
        throws Exception
    {
        testCurve("prime192v1"); // X9.62
        testCurve("sect571r1"); // sec
        testCurve("secp224r1");
        testCurve("B-409");   // nist
        testCurve("P-521");
        testCurve("brainpoolP160r1");    // TeleTrusT

        for (Enumeration en = X962NamedCurves.getNames(); en.hasMoreElements();)
        {
            testECDSA((String)en.nextElement());
        }

        for (Enumeration en = SECNamedCurves.getNames(); en.hasMoreElements();)
        {
            testECDSA((String)en.nextElement());
        }

        for (Enumeration en = TeleTrusTNamedCurves.getNames(); en.hasMoreElements();)
        {
            testECDSA((String)en.nextElement());
        }
    }
    
    public static void main(
        String[]    args)
    {
        Security.addProvider(new BouncyCastleProvider());
    
        runTest(new NamedCurveTest());
    }
}
