package me.anno.scripting.visual

class VisualProperty(
) : NamedVisual() {

    var type: VisualType? = null
    var defaultValue: VisualValue? = null
    var value: VisualValue? = null

    override val className get() = "VisualProperty"

}