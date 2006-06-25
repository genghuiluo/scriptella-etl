/*
 * Copyright 2006 The Scriptella Project Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package scriptella.spi.velocity;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import scriptella.configuration.Resource;
import scriptella.expressions.ParametersCallback;
import scriptella.spi.AbstractConnection;
import scriptella.spi.ProviderException;
import scriptella.spi.QueryCallback;
import scriptella.util.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;

/**
 * Represents a session to velocity engine.
 */
public class VelocityConnection extends AbstractConnection {
    private final URL url;

    private final VelocityEngine engine;
    private final VelocityContextAdapter adapter;
    private Writer writer;//lazy initialized
    private String encoding;//encoding for writer



    /**
     * Instantiates a velocity connection.
     *
     * @param url            URL for output.
     */
    public VelocityConnection(URL url) {
        this(url, null);
    }

    /**
     * Instantiates a velocity connection.
     *
     * @param url            URL for output.
     * @param outputEncoding charset name for output stream. If null default charset is used.
     */
    public VelocityConnection(URL url, String outputEncoding) {
        super(Driver.DIALECT);
        this.url = url;
        engine = new VelocityEngine();
        engine.setProperty(VelocityEngine.RUNTIME_LOG_LOGSYSTEM, Driver.LOG_SYSTEM);
        engine.setProperty("velocimacro.library", "");//unnecessary file in our case
        try {
            engine.init();
        } catch (Exception e) {
            throw new VelocityProviderException("Unable to initialize engine", e);
        }
        adapter = new VelocityContextAdapter();
        encoding = outputEncoding;
    }

    /**
     * Executes a script specified by its content.
     * <p>scriptContent may be used as a key for caching purposes, i.e.
     * provider may precompile scripts and use compiled versions for subsequent executions.
     * <p>This method is synchronized to to prevent multiple threads from working with the same writer.
     * Additionally single velocityEngine and context adapter instances are used.
     *
     * @param scriptContent      script content.
     * @param parametersCallback callback to get parameter values.
     */
    public synchronized void executeScript(Resource scriptContent, ParametersCallback parametersCallback) throws ProviderException {
        //todo Current solution is slow, use per scriptContent caching by providing a custom Velocity ResourceLoader
        //todo also make Resource identifiable, i.e. replace url.getFile with resource name/location
        adapter.setCallback(parametersCallback);//we may use single context+engine because method is synchronized
        Reader reader = null;
        try {
            reader = scriptContent.open();
            engine.evaluate(adapter, getWriter(), url.getFile(), reader);
        } catch (Exception e) {
            throw new VelocityProviderException("Unable to execute script", e);
        } finally {
            adapter.setCallback(null);//cleaning up to avoid mem leaks
            IOUtils.closeSilently(reader);
        }
    }

    /**
     * Executes a query specified by its content.
     * <p/>
     *
     * @param queryContent       query content.
     * @param parametersCallback callback to get parameter values.
     * @param queryCallback      callback to call for each result set element produced by this query.
     * @see #executeScript(scriptella.configuration.Resource, scriptella.expressions.ParametersCallback)
     */
    public void executeQuery(Resource queryContent, ParametersCallback parametersCallback, QueryCallback queryCallback) throws ProviderException {
        throw new UnsupportedOperationException("Query execution is not supported yet");
    }

    private Writer getWriter() {
        if (writer == null) {
            try {
                URLConnection con = url.openConnection();
                final OutputStream os = con.getOutputStream();
                writer = new BufferedWriter(encoding == null ? new OutputStreamWriter(os) :
                        new OutputStreamWriter(os, encoding));
            } catch (IOException e) {
                throw new VelocityProviderException("Unable to open URL " + url, e);
            }

        }
        return writer;
    }


    /**
     * Closes the connection and releases all related resources.
     */
    public synchronized void close() throws ProviderException {
        if (writer != null) {
            IOUtils.closeSilently(writer);
            writer = null;
        }
    }

    /**
     * Velocity Context adapter class for {@link ParametersCallback}.
     */
    private static class VelocityContextAdapter implements Context {
        private ParametersCallback callback;

        public ParametersCallback getCallback() {
            return callback;
        }

        public void setCallback(ParametersCallback callback) {
            this.callback = callback;
        }

        public Object put(String key, Object value) {
            throw new UnsupportedOperationException();
        }

        public Object get(String key) {
            return callback.getParameter(key);
        }

        public boolean containsKey(Object key) {
            return false;
        }

        public Object[] getKeys() {
            throw new UnsupportedOperationException();
        }

        public Object remove(Object key) {
            throw new UnsupportedOperationException();
        }
    }

}
