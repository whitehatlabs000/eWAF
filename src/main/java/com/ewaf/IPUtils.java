package com.ewaf;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IPUtils {

    private static final Logger log = LoggerFactory.getLogger(IPUtils.class);

    /**
     * Lista de proxies confiables. Pueden ser IP individuales o rangos CIDR.
     * Usamos CopyOnWriteArrayList para seguridad en concurrencia (Thread-Safe).
     */
    private static final List<IPRange> trustedProxyRanges = new CopyOnWriteArrayList<>();

    private static final String[] HEADERS_TO_CHECK = {
            "CF-Connecting-IP", // Cloudflare (Alta prioridad)
            "X-Real-IP",        // Nginx/Apache estándar
            "X-Forwarded-For"   // Estándar general (Lista de IPs)
    };

    // Inicialización por defecto (Localhost)
    static {
        init("127.0.0.1, ::1, 0:0:0:0:0:0:0:1");
    }

    /**
     * Inicializa la lista de proxies confiables. Soporta CIDR (e.g., 10.0.0.0/8)
     */
    public static void init(String trustedProxiesStr) {
        trustedProxyRanges.clear();

        if (trustedProxiesStr == null || trustedProxiesStr.isBlank()) {
            return;
        }

        for (String entry : trustedProxiesStr.split(",")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;

            try {
                trustedProxyRanges.add(IPRange.parse(entry));
            } catch (Exception ignored) {
                // Si hay un error en el formato de una IP del config, la saltamos para no romper el arranque
                log.warn("IPUtils: Error parsing trusted IP/CIDR '{}': {}", entry, ignored.getMessage());
            }
        }
    }

    /**
     * Devuelve la IP real del cliente.
     */
    public static String getClientIp(HttpServletRequest request) {
        String remoteAddr = normalizeIp(request.getRemoteAddr());

        // 1. Si la conexión directa NO viene de un proxy confiable entonces devolver esa IP directa
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        // 2. Si es un proxy confiable, buscamos la IP real en los headers
        for (String header : HEADERS_TO_CHECK) {
            String value = request.getHeader(header);
            if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value)) continue;

            // Caso especial: X-Forwarded-For puede ser una lista "client, proxy1, proxy2"
            if (header.equalsIgnoreCase("X-Forwarded-For")) {
                String realIp = extractFromXForwardedFor(value);
                if (realIp != null) return realIp;
            } else {
                // Headers de IP única (CF-Connecting-IP, X-Real-IP)
                String ip = value.trim();
                if (isValidIp(ip)) {
                    return normalizeIp(ip);
                }
            }
        }

        // 3. Si confiamos en el proxy pero no trajo headers válidos → devolvemos la IP del proxy
        return remoteAddr;
    }

    /**
     * Extrae la IP real desde X-Forwarded-For.
     * Recorremos la cadena AL REVÉS (Derecha a Izquierda).
     * La IP más a la derecha es la que se conectó al último proxy confiable.
     * Retrocedemos saltando IPs de confianza hasta encontrar la primera desconocida (el cliente).
     */
    private static String extractFromXForwardedFor(String headerValue) {
        // VALIDACIÓN DE MEMORIA: Prevenir ataques de saturación
        if (headerValue.length() > 1000) {
            log.warn("IPUtils Alert: X-Forwarded-For header abnormally long ({} chars). Ignoring due to potential DoS attack.", headerValue.length());
            return null; // El método padre se encargará de ignorarlo y usar el fallback
        }

        String[] ips = headerValue.split(",");

        // Iteramos desde el final hacia el principio
        for (int i = ips.length - 1; i >= 0; i--) {
            String raw = ips[i];
            String ip = normalizeIp(raw.trim());

            // Si encontramos una IP válida que NO es un proxy de confianza, ESA es la IP del cliente.
            if (isValidIp(ip) && !isTrustedProxy(ip)) {
                return ip;
            }
        }

        // Si recorrimos todo y todas eran de confianza (o inválidas), devolvemos la última válida conocida
        // (la más cercana a nuestro servidor).
        return normalizeIp(ips[ips.length - 1].trim());
    }

    /**
     * Verifica si una IP está dentro de la lista de proxies confiables.
     */
    private static boolean isTrustedProxy(String ip) {
        if (!isValidIp(ip)) return false;
        InetAddress addr = toInetAddress(ip);
        if (addr == null) return false;

        for (IPRange range : trustedProxyRanges) {
            if (range.contains(addr)) return true;
        }
        return false;
    }

    /**
     * Normaliza IPv6 localhost a IPv4, quita compresión y valida nulos.
     */
    private static String normalizeIp(String ip) {
        if (ip == null) return ""; // Protección contra nulos

        // Normalización rápida de strings conocidos
        if (ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")) {
            return "127.0.0.1";
        }

        // VERIFICACIÓN DE SEGURIDAD: Prevenir DNS Lookup de basura
        if (!ip.matches("^[0-9a-fA-F:.]+$")) {
            return ip;
        }

        // Normalización canónica usando InetAddress
        try {
            InetAddress inet = InetAddress.getByName(ip);
            return inet.getHostAddress();
        } catch (Exception e) {
            return ip; // Si falla el parseo, devolvemos la original
        }
    }

    /**
     * Valida estrictamente si una cadena es una IP.
     * NO genera DNS lookups costosos.
     */
    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) return false;
        try {
            // getByAddress valida el formato sin contactar servidores DNS
            return toInetAddress(ip) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static InetAddress toInetAddress(String ip) {
        try {
            // VERIFICACIÓN DE SEGURIDAD: Previene búsquedas DNS maliciosas (DoS).
            // Validamos que la cadena contenga solo caracteres permitidos en IPv4 o IPv6
            // antes de pasarlo a InetAddress.getByName()
            if (!ip.matches("^[0-9a-fA-F:.]+$")) {
                return null;
            }
            return InetAddress.getByName(ip);
        } catch (Exception e) {
            return null;
        }
    }

    // =============================================================
    //                 CLASE AUXILIAR PARA RANGOS CIDR
    // =============================================================

    private static class IPRange {
        private final InetAddress start;
        private final InetAddress end;

        private IPRange(InetAddress start, InetAddress end) {
            this.start = start;
            this.end = end;
        }

        static IPRange parse(String value) throws UnknownHostException {
            if (value.contains("/")) {
                return fromCIDR(value);
            }
            InetAddress addr = InetAddress.getByName(value);
            return new IPRange(addr, addr);
        }

        static IPRange fromCIDR(String cidr) throws UnknownHostException {
            String[] parts = cidr.split("/");
            InetAddress base = InetAddress.getByName(parts[0]);
            int prefix = Integer.parseInt(parts[1]);

            byte[] address = base.getAddress();
            byte[] mask = prefixToMask(prefix, address.length * 8);

            byte[] start = new byte[address.length];
            byte[] end = new byte[address.length];

            for (int i = 0; i < address.length; i++) {
                start[i] = (byte) (address[i] & mask[i]);
                end[i] = (byte) ((address[i] & mask[i]) | ~mask[i]);
            }

            return new IPRange(InetAddress.getByAddress(start), InetAddress.getByAddress(end));
        }

        static byte[] prefixToMask(int prefix, int totalBits) {
            byte[] mask = new byte[totalBits / 8];
            for (int i = 0; i < mask.length; i++) {
                int bits = Math.min(8, Math.max(0, prefix - i * 8));
                mask[i] = (byte) (0xFF << (8 - bits));
            }
            return mask;
        }

        boolean contains(InetAddress addr) {
            byte[] target = addr.getAddress();
            // Prevenir comparación entre IPv4 e IPv6 (longitudes distintas)
            if (target.length != start.getAddress().length) return false;

            byte[] s = start.getAddress();
            byte[] e = end.getAddress();

            // Comparación byte a byte unsigned
            for (int i = 0; i < target.length; i++) {
                int t = target[i] & 0xFF;
                int min = s[i] & 0xFF;
                int max = e[i] & 0xFF;

                if (t < min || t > max) return false;
            }
            return true;
        }
    }
}