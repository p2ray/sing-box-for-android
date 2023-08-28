package com.github.foxray.bg

import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import io.nekohasekai.libbox.TunOptions
import com.github.foxray.database.Settings

class VPNService : VpnService(), PlatformInterfaceWrapper {

    companion object {
        private const val TAG = "VPNService"
    }

    private val service = BoxService(this, this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) =
        service.onStartCommand(intent, flags, startId)

    override fun onBind(intent: Intent) = service.onBind(intent)
    override fun onDestroy() {
        service.onDestroy()
    }

    override fun onRevoke() {
        service.onRevoke()
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        protect(fd)
    }

    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("android: missing vpn permission")

        val builder = Builder()
            .setSession("sing-box")
            .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val inet4Address = options.inet4Address
        if (inet4Address.hasNext()) {
            while (inet4Address.hasNext()) {
                val address = inet4Address.next()
                builder.addAddress(address.address, address.prefix)
            }
        }

        val inet6Address = options.inet6Address
        if (inet6Address.hasNext()) {
            while (inet6Address.hasNext()) {
                val address = inet6Address.next()
                builder.addAddress(address.address, address.prefix)
            }
        }

        if (options.autoRoute) {
            builder.addDnsServer(options.dnsServerAddress)

            val inet4RouteAddress = options.inet4RouteAddress
            if (inet4RouteAddress.hasNext()) {
                while (inet4RouteAddress.hasNext()) {
                    val address = inet4RouteAddress.next()
                    builder.addRoute(address.address, address.prefix)
                }
            } else {
                builder.addRoute("0.0.0.0", 0)
            }

            val inet6RouteAddress = options.inet6RouteAddress
            if (inet6RouteAddress.hasNext()) {
                while (inet6RouteAddress.hasNext()) {
                    val address = inet6RouteAddress.next()
                    builder.addRoute(address.address, address.prefix)
                }
            } else {
                builder.addRoute("::", 0)
            }

            if (Settings.perAppProxyEnabled) {
                val appList = Settings.perAppProxyList
                if (Settings.perAppProxyMode == Settings.PER_APP_PROXY_INCLUDE) {
                    appList.forEach {
                        try {
                            builder.addAllowedApplication(it)
                        } catch (_: NameNotFoundException) {
                        }
                    }
                    builder.addAllowedApplication(packageName)
                } else {
                    appList.forEach {
                        try {
                            builder.addDisallowedApplication(it)
                        } catch (_: NameNotFoundException) {
                        }
                    }
                }
            } else {
                val includePackage = options.includePackage
                if (includePackage.hasNext()) {
                    while (includePackage.hasNext()) {
                        try {
                            builder.addAllowedApplication(includePackage.next())
                        } catch (_: NameNotFoundException) {
                        }
                    }
                }

                val excludePackage = options.excludePackage
                if (excludePackage.hasNext()) {
                    while (excludePackage.hasNext()) {
                        try {
                            builder.addDisallowedApplication(excludePackage.next())
                        } catch (_: NameNotFoundException) {
                        }
                    }
                }
            }
        }

        if (options.isHTTPProxyEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setHttpProxy(
                    ProxyInfo.buildDirectProxy(
                        options.httpProxyServer,
                        options.httpProxyServerPort
                    )
                )
            } else {
                error("android: tun.platform.http_proxy requires android 10 or higher")
            }
        }

        val pfd =
            builder.establish() ?: error("android: the application is not prepared or is revoked")
        service.fileDescriptor = pfd
        return pfd.fd
    }

    override fun writeLog(message: String) = service.writeLog(message)

}