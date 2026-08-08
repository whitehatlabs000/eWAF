package com.ewaf;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private static final Logger log = LoggerFactory.getLogger(CachedBodyHttpServletRequest.class);

    private byte[] cachedBody;
    private Map<String, String[]> cachedParameters;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        InputStream requestInputStream = request.getInputStream();

        // 1. LIMITAR TAMAÑO: 2MB máximo (Protección DoS)
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        int totalBytes = 0;
        int maxBytes = 2 * 1024 * 1024; // 2 MB

        while ((nRead = requestInputStream.read(data, 0, data.length)) != -1) {
            totalBytes += nRead;
            if (totalBytes > maxBytes) {
                throw new IOException("Body too large");
            }
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        this.cachedBody = buffer.toByteArray();

        // 2. PARSEO DE PARÁMETROS
        // Si es un formulario estándar, parseamos los bytes nosotros mismos
        // porque Tomcat ya no puede leer el stream que acabamos de consumir.
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("application/x-www-form-urlencoded")) {
            parseParameters();
        }
    }

    private void parseParameters() {
        cachedParameters = new HashMap<>();
        // Copiamos params que ya existieran en la URL (Query String)
        if (super.getParameterMap() != null) {
            cachedParameters.putAll(super.getParameterMap());
        }

        String body = new String(this.cachedBody, StandardCharsets.UTF_8);
        if (body.isEmpty()) return;

        // Formato esperado: key=value&key2=value2
        String[] pairs = body.split("&");

        // VALIDACIÓN TEMPRANA (Escudo DoS Hash Collision):
        // Tomcat limita a 10,000 parámetros por defecto. Nosotros lo limitamos a 1000.
        int paramCount = cachedParameters.size();
        final int MAX_PARAMS = 1000;

        for (String pair : pairs) {
            if (paramCount >= MAX_PARAMS) {
                log.warn("DoS SHIELD: Max parameter count ({}) exceeded. Dropping remaining parameters to prevent Hash Collision DoS.", MAX_PARAMS);
                break; // Cortamos el procesamiento para salvar la CPU
            }

            // Usamos split("=", 2) para evitar crear arreglos masivos si el atacante envía "a=b=c=d=e..."
            // y porque en application/x-www-form-urlencoded el primer "=" separa clave de valor.
            String[] fields = pair.split("=", 2);
            try {
                String name = URLDecoder.decode(fields[0], StandardCharsets.UTF_8);
                String value = (fields.length > 1) ? URLDecoder.decode(fields[1], StandardCharsets.UTF_8) : "";

                String[] currentValues = cachedParameters.get(name);
                if (currentValues == null) {
                    cachedParameters.put(name, new String[]{value});
                    paramCount++; // Contamos solo claves únicas nuevas
                } else {
                    // Si ya existe (ej: checkbox multiple), agregamos al array
                    String[] newValues = Arrays.copyOf(currentValues, currentValues.length + 1);
                    newValues[currentValues.length] = value;
                    cachedParameters.put(name, newValues);
                }
            } catch (IllegalArgumentException e) {
                // Ignorar pares mal formados
            }
        }
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(this.cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
        return new BufferedReader(new InputStreamReader(byteArrayInputStream, StandardCharsets.UTF_8));
    }

    public String getBody() {
        return new String(this.cachedBody, StandardCharsets.UTF_8);
    }

    // --- OVERRIDES DE PARÁMETROS ---

    @Override
    public String getParameter(String name) {
        if (cachedParameters != null) {
            String[] values = cachedParameters.get(name);
            return (values != null && values.length > 0) ? values[0] : null;
        }
        return super.getParameter(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        if (cachedParameters != null) {
            return Collections.unmodifiableMap(cachedParameters);
        }
        return super.getParameterMap();
    }

    @Override
    public Enumeration<String> getParameterNames() {
        if (cachedParameters != null) {
            return Collections.enumeration(cachedParameters.keySet());
        }
        return super.getParameterNames();
    }

    @Override
    public String[] getParameterValues(String name) {
        if (cachedParameters != null) {
            return cachedParameters.get(name);
        }
        return super.getParameterValues(name);
    }

    // Clase interna para manejar el Stream
    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final InputStream cachedBodyInputStream;

        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.cachedBodyInputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            try {
                return cachedBodyInputStream.available() == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() throws IOException {
            return cachedBodyInputStream.read();
        }
    }
}