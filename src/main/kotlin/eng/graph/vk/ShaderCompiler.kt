package eng.graph.vk

import org.lwjgl.*
import org.lwjgl.system.*
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK12.*
import org.tinylog.kotlin.Logger
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files

class ShaderCompiler {
    companion object {
        fun compileShaderIfChanged(glslShaderFile: String, shaderType: Int) {
            val compiledShader: ByteArray
            try {
                val glslFile = File(glslShaderFile)
                val spvFile = File("$glslShaderFile.spv")
                if (!spvFile.exists() || glslFile.lastModified() > spvFile.lastModified()) {
                    Logger.debug("Compiling [{}] to [{}]", glslFile.path, spvFile.path)
                    val shaderCode = String(Files.readAllBytes(glslFile.toPath()))
                    compiledShader = compileShader(shaderCode, shaderType)
                    Files.write(spvFile.toPath(), compiledShader)
                } else {
                    Logger.debug("Shader [{}] already compiled. Loading compiled version: [{}]",
                        glslFile, spvFile)
                }
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
        fun compileShader(shaderCode: String, shaderType: Int): ByteArray {
            var compiler: Long = 0
            var options: Long = 0
            val compiledShader: ByteArray
            try {
                compiler = Shaderc.shaderc_compiler_initialize()
                options = Shaderc.shaderc_compile_options_initialize()
                val result = Shaderc.shaderc_compile_into_spv(
                    compiler,
                    shaderCode,
                    shaderType,
                    "shader.glsl",
                    "main",
                    options
                )
                if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                    throw RuntimeException("Shader compilation failed: ${Shaderc.shaderc_result_get_error_message(result)}")
                }
                val buffer: ByteBuffer = Shaderc.shaderc_result_get_bytes(result)!!
                compiledShader = ByteArray(buffer.remaining())
                buffer.get(compiledShader)
            } finally {
                Shaderc.shaderc_compile_options_release(options)
                Shaderc.shaderc_compiler_release(compiler)
            }
            return compiledShader
        }
    }
}