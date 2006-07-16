package org.bouncycastle.tsp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.X509Extension;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.tsp.TimeStampReq;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.x509.X509Extensions;

/**
 * Base class for an RFC 3161 Time Stamp Request.
 */
public class TimeStampRequest
    implements X509Extension
{
    TimeStampReq req;

    public TimeStampRequest(TimeStampReq req)
    {
        this.req = req;
    }

    /**
     * Create a TimeStampRequest from the past in byte array.
     * 
     * @param req byte array containing the request.
     * @throws IOException if the request is malformed.
     */
    public TimeStampRequest(byte[] req) 
        throws IOException
    {
        this(new ByteArrayInputStream(req));
    }

    /**
     * Create a TimeStampRequest from the past in input stream.
     * 
     * @param in input stream containing the request.
     * @throws IOException if the request is malformed.
     */
    public TimeStampRequest(InputStream in) 
        throws IOException
    {
        try
        {
            this.req = TimeStampReq.getInstance(new ASN1InputStream(in).readObject());
        }
        catch (ClassCastException e)
        {
            throw new IOException("malformed request: " + e);
        }
        catch (IllegalArgumentException e)
        {
            throw new IOException("malformed request: " + e);
        }
    }

    public int getVersion()
    {
        return req.getVersion().getValue().intValue();
    }

    public String getMessageImprintAlgOID()
    {
        return req.getMessageImprint().getHashAlgorithm().getObjectId().getId();
    }

    public byte[] getMessageImprintDigest()
    {
        return req.getMessageImprint().getHashedMessage();
    }

    public String getReqPolicy()
    {
        if (req.getReqPolicy() != null)
        {
            return req.getReqPolicy().getId();
        }
        else
        {
            return null;
        }
    }

    public BigInteger getNonce()
    {
        if (req.getNonce() != null)
        {
            return req.getNonce().getValue();
        }
        else
        {
            return null;
        }
    }

    public boolean getCertReq()
    {
        if (req.getCertReq() != null)
        {
            return req.getCertReq().isTrue();
        }
        else
        {
            return false;
        }
    }

    /**
     * Validate the timestamp request, checking the digest to see if it is of an
     * accepted type and whether it is of the correct length for the algorithm specified.
     * 
     * @param algorithms a set of String OIDS giving accepted algorithms.
     * @param policies if non-null a set of policies we are willing to sign under.
     * @param extensions if non-null a set of extensions we are willing to accept.
     * @param provider the provider to confirm the digest size against.
     * @throws TSPException if the request is invalid, or processing fails.
     */
    public void validate(
        Set     algorithms,
        Set     policies,
        Set     extensions,
        String  provider)
        throws TSPException, NoSuchProviderException
    {
        if (!algorithms.contains(this.getMessageImprintAlgOID()))
        {
            throw new TSPValidationException("request contains unknown algorithm.", PKIFailureInfo.badAlg);
        }
        
        if (policies != null && this.getReqPolicy() != null && !policies.contains(this.getReqPolicy()))
        {
            throw new TSPValidationException("request contains unknown policy.", PKIFailureInfo.unacceptedPolicy);
        }
        
        if (this.getExtensions() != null && extensions != null)
        {
            Enumeration en = this.getExtensions().oids();
            while(en.hasMoreElements())
            {
                String  oid = ((DERObjectIdentifier)en.nextElement()).getId();
                if (!extensions.contains(oid))
                {
                    throw new TSPValidationException("request contains unknown extension.", PKIFailureInfo.unacceptedExtension);
                }
            }
        }
        
        String digestName = TSPUtil.getDigestAlgName(this.getMessageImprintAlgOID());
        
        try
        {
            checkImprintLength(MessageDigest.getInstance(digestName, provider));
        }
        catch (NoSuchAlgorithmException e)
        {
            try
            {
                checkImprintLength(MessageDigest.getInstance(digestName));
            }
            catch (NoSuchAlgorithmException ex)
            {
                throw new TSPException("digest algorithm cannot be found.", ex);
            }
        }
    }

    private void checkImprintLength(MessageDigest d) 
        throws TSPValidationException
    {
        if (d.getDigestLength() != this.getMessageImprintDigest().length)
        {
            throw new TSPValidationException("imprint digest the wrong length.", PKIFailureInfo.badDataFormat);
        }
    }
    
   /**
    * return the ASN.1 encoded representation of this object.
    */
    public byte[] getEncoded() throws IOException
    {
        return req.getEncoded();
    }

    X509Extensions getExtensions()
    {
        return req.getExtensions();
    }
    
    /* (non-Javadoc)
     * @see java.security.cert.X509Extension#getExtensionValue(java.lang.String)
     */
    public byte[] getExtensionValue(String oid)
    {
        X509Extensions exts = req.getExtensions();

        if (exts != null)
        {
            org.bouncycastle.asn1.x509.X509Extension   ext = exts.getExtension(new DERObjectIdentifier(oid));

            if (ext != null)
            {
                try
                {
                    return ext.getValue().getEncoded();
                }
                catch (Exception e)
                {
                    throw new RuntimeException("error encoding " + e.toString());
                }
            }
        }

        return null;
    }
    
    private Set getExtensionOIDS(
        boolean critical)
    {
        Set             set = new HashSet();
        X509Extensions  extensions = req.getExtensions();

        if (extensions != null)
        {
            Enumeration     e = extensions.oids();

            while (e.hasMoreElements())
            {
                DERObjectIdentifier                      oid = (DERObjectIdentifier)e.nextElement();
                org.bouncycastle.asn1.x509.X509Extension ext = extensions.getExtension(oid);

                if (ext.isCritical() == critical)
                {
                    set.add(oid.getId());
                }
            }

            return set;
        }

        return null;
    }

    public Set getNonCriticalExtensionOIDs() 
    {
        return getExtensionOIDS(false);
    }
    
    public Set getCriticalExtensionOIDs()
    {
        return getExtensionOIDS(true);
    }
    
    public boolean hasUnsupportedCriticalExtension()
    {
        return false;
    }
}
