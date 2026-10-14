package com.gen.keymapper

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.util.Date
import java.util.concurrent.TimeUnit

/** A persistent, app-private identity; never save the six-digit pairing code. */
class AdbIdentity(context: Context) : AbsAdbConnectionManager() {
    private val privateKey: PrivateKey
    private val certificate: Certificate
    init {
        val file = AtomicFile(File(context.noBackupFilesDir, "adb-identity.p12"))
        val password = CharArray(0)
        val store = KeyStore.getInstance("PKCS12")
        if (file.baseFile.exists()) file.openRead().use { store.load(it, password) }
        else {
            store.load(null, password)
            val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val name = X500Name("CN=GenKey")
            val now = System.currentTimeMillis()
            val provider = BouncyCastleProvider()
            val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(keys.private)
            val cert = JcaX509CertificateConverter().setProvider(provider).getCertificate(
                JcaX509v3CertificateBuilder(name, BigInteger(128, SecureRandom()),
                    Date(now - 86_400_000L), Date(now + 315_360_000_000L), name, keys.public).build(signer))
            store.setKeyEntry("genkey", keys.private, password, arrayOf(cert))
            val output = file.startWrite()
            try { store.store(output, password); file.finishWrite(output) }
            catch (t: Throwable) { file.failWrite(output); throw t }
        }
        privateKey = store.getKey("genkey", password) as PrivateKey
        certificate = store.getCertificate("genkey")
        setApi(Build.VERSION.SDK_INT)
        setTimeout(10, TimeUnit.SECONDS)
    }
    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "GenKey"
}
