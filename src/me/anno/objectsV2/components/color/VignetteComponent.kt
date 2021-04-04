package me.anno.objectsV2.components.color

import me.anno.gpu.shader.Shader
import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.objects.animation.AnimatedProperty
import me.anno.objectsV2.Component
import me.anno.objectsV2.components.shaders.FragmentShaderComponent
import me.anno.objectsV2.components.shaders.ShaderEnvironment
import me.anno.objectsV2.components.shaders.VariableType
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.style.Style

class VignetteComponent: Component(), FragmentShaderComponent {

    val strength = AnimatedProperty.float(1f)

    override fun getShaderComponent(env: ShaderEnvironment): String {
        // "       float rSq = dot(nuv,nuv);\n" + needs to be added to the general shader...
        return "color = mix(" +
                "   vec4(${env[this, "color", VariableType.UNIFORM_V3]}, 1.0)," +
                "   color," +
                "   1.0/(1.0 + ${env[this, "strength", VariableType.UNIFORM_V1]}*rSq)" +
                ");\n"
    }

    override fun getShaderCodeState(): Any? = null

    override fun bindUniforms(shader: Shader, env: ShaderEnvironment, time: Double) {
        shader.v3X(env[this, "color", VariableType.UNIFORM_V3], color[time])
        shader.v1(env[this, "strength", VariableType.UNIFORM_V1], strength[time])
    }

    override fun createInspector(
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        // todo name and description?...
        val group = getGroup("Component", "", "")
        group += vi("Color", "", color, style)
        group += vi("Strength", "", strength, style)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "strength", strength)
    }

    override fun readObject(name: String, value: ISaveable?) {
        when(name){
            "strength" -> strength.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }


}