package com.watube.yard.cast

import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import com.watube.yard.cast.proto.AuthChallenge
import com.watube.yard.cast.proto.CastMessage
import com.watube.yard.cast.proto.DeviceAuthMessage
import com.watube.yard.cast.proto.HashAlgorithm
import com.watube.yard.cast.proto.SignatureAlgorithm
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import java.util.concurrent.TimeUnit

// region Stolm
/** The receiver failed to prove it is a genuine Cast device owning this connection. */
class CastAuthException(reason: String) : IOException(reason)

/**
 * Device authentication of the Cast v2 protocol, as Chromium / Open Screen check it
 * (cast_auth_util.cc, boringssl_trust_store.cc, BSD-3-Clause): the receiver signs our random
 * nonce followed by the TLS certificate it presented, with a key whose certificate chains up
 * to a Cast root. A device that cannot is refused before anything else is sent to it.
 *
 * @param trustAnchors the Cast roots (see [CastTrustStore])
 * @param now verification time (fixed in tests)
 */
internal class CastDeviceAuth(
    private val trustAnchors: Set<TrustAnchor>,
    private val now: () -> Date = ::Date
) {
    private val nonce = ByteArray(NONCE_SIZE).also(SecureRandom()::nextBytes)

    /** The challenge to send first, on [CastNamespaces.DEVICE_AUTH]. */
    fun challenge(): CastMessage = CastMessages.binary(
        CastNamespaces.DEVICE_AUTH,
        DeviceAuthMessage.newBuilder()
            .setChallenge(
                AuthChallenge.newBuilder()
                    .setSenderNonce(ByteString.copyFrom(nonce))
                    .setSignatureAlgorithm(SignatureAlgorithm.RSASSA_PKCS1v15)
                    .setHashAlgorithm(HashAlgorithm.SHA256)
            )
            .build()
            .toByteArray()
    )

    /** Throws [CastAuthException] unless [reply] authenticates the connection of [peerCert]. */
    fun verify(reply: CastMessage, peerCert: X509Certificate) {
        if (reply.namespace != CastNamespaces.DEVICE_AUTH || !reply.hasPayloadBinary()) {
            throw CastAuthException("no device authentication reply")
        }
        val message = try {
            DeviceAuthMessage.parseFrom(reply.payloadBinary)
        } catch (e: InvalidProtocolBufferException) {
            throw CastAuthException("unreadable device authentication reply")
        }
        if (!message.hasResponse()) throw CastAuthException("device authentication refused: ${message.error.errorType}")
        val response = message.response
        val time = now()

        checkTlsCertificate(peerCert, time)
        // constant time: the nonce is the secret of this exchange
        if (!MessageDigest.isEqual(response.senderNonce.toByteArray(), nonce)) {
            throw CastAuthException("sender nonce mismatch")
        }

        val chain = (listOf(response.clientAuthCertificate) + response.intermediateCertificateList)
            .map { parseCertificate(it.toByteArray()) }
        val deviceCert = verifyChain(chain, trustAnchors, time)

        verifySignature(
            deviceCert,
            response.hashAlgorithm,
            signed = nonce + peerCert.encoded,
            signature = response.signature.toByteArray()
        )
    }

    internal companion object {
        const val NONCE_SIZE = 16

        /** A receiver's TLS certificate must not live longer than this (Open Screen value). */
        private val MAX_TLS_CERT_LIFETIME_MS = TimeUnit.DAYS.toMillis(4)

        /** Smallest RSA key accepted for the device certificate. */
        private const val MIN_RSA_BITS = 2048

        /** keyUsage bit of digitalSignature */
        private const val DIGITAL_SIGNATURE = 0

        private val certificateFactory = CertificateFactory.getInstance("X.509")

        fun parseCertificate(der: ByteArray): X509Certificate = try {
            certificateFactory.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        } catch (e: GeneralSecurityException) {
            throw CastAuthException("unreadable certificate")
        }

        /**
         * The TLS certificate is self-signed and not checked as X.509: the signature of the
         * device binds it, its validity only says how long that signature may be trusted.
         */
        fun checkTlsCertificate(cert: X509Certificate, time: Date) =
            checkTlsValidity(cert.notBefore, cert.notAfter, time)

        fun checkTlsValidity(notBefore: Date, notAfter: Date, time: Date) {
            if (time.before(notBefore)) throw CastAuthException("TLS certificate not valid yet")
            if (time.after(notAfter)) throw CastAuthException("TLS certificate expired")
            if (notAfter.time > time.time + MAX_TLS_CERT_LIFETIME_MS) {
                throw CastAuthException("TLS certificate lifetime too long")
            }
        }

        /**
         * Validates [chain] (device certificate first) up to one of [anchors], at [time], without
         * revocation lists (optional for senders in Open Screen). Returns the device certificate.
         */
        fun verifyChain(chain: List<X509Certificate>, anchors: Set<TrustAnchor>, time: Date): X509Certificate {
            val deviceCert = chain.firstOrNull() ?: throw CastAuthException("no device certificate")

            val key = deviceCert.publicKey as? RSAPublicKey ?: throw CastAuthException("device key is not RSA")
            if (key.modulus.bitLength() < MIN_RSA_BITS) throw CastAuthException("device key too short")
            if (deviceCert.keyUsage?.getOrNull(DIGITAL_SIGNATURE) != true) {
                throw CastAuthException("device certificate may not sign")
            }

            val params = PKIXParameters(anchors).apply {
                isRevocationEnabled = false
                date = time
            }
            try {
                CertPathValidator.getInstance("PKIX")
                    .validate(certificateFactory.generateCertPath(chain), params)
            } catch (e: GeneralSecurityException) {
                throw CastAuthException("device certificate not issued by a Cast root")
            }
            return deviceCert
        }

        fun verifySignature(cert: X509Certificate, hash: HashAlgorithm, signed: ByteArray, signature: ByteArray) {
            val algorithm = when (hash) {
                HashAlgorithm.SHA256 -> "SHA256withRSA"
                // older receivers answer SHA-1 whatever is asked (accepted by Open Screen too)
                HashAlgorithm.SHA1 -> "SHA1withRSA"
            }
            val valid = try {
                Signature.getInstance(algorithm).run {
                    initVerify(cert.publicKey)
                    update(signed)
                    verify(signature)
                }
            } catch (e: GeneralSecurityException) {
                false
            }
            if (!valid) throw CastAuthException("bad device signature")
        }
    }
}
// endregion
