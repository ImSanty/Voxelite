package com.microvoxel.render;

import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniform4f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glUseProgram;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

/**
 * Programa de shaders GLSL compilado y enlazado.
 *
 * <p>Centraliza la compilacion (con informes de error legibles), el enlazado, la
 * consulta de uniformes (con cache de localizaciones) y la liberacion del
 * recurso nativo. Ningun otro componente compila shaders directamente.</p>
 */
public final class ShaderProgram {

    private final int programId;
    private final String name;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    private ShaderProgram(int programId, String name) {
        this.programId = programId;
        this.name = name;
    }

    /**
     * Crea un programa a partir de dos recursos de shader.
     *
     * @param name       nombre descriptivo (para mensajes de error)
     * @param vertexPath ruta del vertex shader dentro de {@code /shaders/}
     * @param fragmentPath ruta del fragment shader dentro de {@code /shaders/}
     */
    public static ShaderProgram fromResources(String name, String vertexPath, String fragmentPath) {
        String vertexSource = readSource(vertexPath);
        String fragmentSource = readSource(fragmentPath);

        int vertex = compile(GL_VERTEX_SHADER, vertexPath, vertexSource);
        int fragment = compile(GL_FRAGMENT_SHADER, fragmentPath, fragmentSource);

        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);

        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            glDeleteShader(vertex);
            glDeleteShader(fragment);
            throw new IllegalStateException("Error al enlazar el programa '" + name + "':\n" + log);
        }

        // Los shaders ya estan enlazados y pueden eliminarse.
        glDeleteShader(vertex);
        glDeleteShader(fragment);

        return new ShaderProgram(program, name);
    }

    private static String readSource(String path) {
        String resource = "/shaders/" + path;
        try (InputStream in = ShaderProgram.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No se encontro el recurso de shader " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el shader " + resource, e);
        }
    }

    private static int compile(int type, String path, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Error al compilar el shader " + path + ":\n" + log);
        }
        return shader;
    }

    public void bind() {
        glUseProgram(programId);
    }

    public static void unbind() {
        glUseProgram(0);
    }

    private int location(String uniform) {
        Integer cached = uniformLocations.get(uniform);
        if (cached != null) {
            return cached;
        }
        int location = glGetUniformLocation(programId, uniform);
        uniformLocations.put(uniform, location);
        return location;
    }

    public void setMatrix4(String uniform, Matrix4f value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            value.get(buffer);
            glUniformMatrix4fv(location(uniform), false, buffer);
        }
    }

    public void setVector3(String uniform, Vector3f value) {
        glUniform3f(location(uniform), value.x, value.y, value.z);
    }

    public void setVector3(String uniform, float x, float y, float z) {
        glUniform3f(location(uniform), x, y, z);
    }

    public void setVector4(String uniform, float x, float y, float z, float w) {
        glUniform4f(location(uniform), x, y, z, w);
    }

    public void setFloat(String uniform, float value) {
        glUniform1f(location(uniform), value);
    }

    public int id() {
        return programId;
    }

    public String name() {
        return name;
    }

    public void close() {
        if (programId != 0) {
            glDeleteProgram(programId);
        }
    }
}
