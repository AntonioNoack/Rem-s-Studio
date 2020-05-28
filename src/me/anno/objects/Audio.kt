package me.anno.objects

import me.anno.ui.style.Style
import org.joml.Matrix4fStack
import org.joml.Vector4f
import java.io.File

// todo flat playback vs 3D playback
// todo use the align-with-camera param for that? :)
class Audio(var file: File, parent: Transform?): GFXTransform(parent){

    // todo we need a flag, whether we draw in editor mode or not
    // todo or a separate function???
    // todo a separate mode, where resource availability is enforced?

    override fun onDraw(stack: Matrix4fStack, time: Float, color: Vector4f) {

    }

}