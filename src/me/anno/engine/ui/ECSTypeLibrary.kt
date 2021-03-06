package me.anno.engine.ui

import me.anno.config.DefaultConfig
import me.anno.engine.ECSWorld
import me.anno.language.translation.Dict
import me.anno.objects.inspectable.Inspectable
import me.anno.ui.base.Panel
import me.anno.ui.custom.Type
import me.anno.ui.custom.UITypeLibrary
import me.anno.ui.editor.PropertyInspector
import me.anno.ui.editor.TimelinePanel
import me.anno.ui.editor.cutting.CuttingView
import me.anno.ui.editor.files.FileExplorer
import me.anno.ui.editor.graphs.GraphEditor
import me.anno.ui.editor.sceneView.SceneView

class ECSTypeLibrary(val world: ECSWorld, val isGaming: Boolean) {

    var selection: Inspectable? = world

    val typeList = listOf<Pair<String, () -> Panel>>(
        // todo not all stuff here makes sense
        // todo some stuff is (maybe) missing, e.g. animation panels, particle system editors, ...
        Dict["Scene View", "ui.customize.sceneView"] to { SceneView(DefaultConfig.style) },
        Dict["Tree View", "ui.customize.treeView"] to { ECSTreeView(this, isGaming, DefaultConfig.style) },
        Dict["Properties", "ui.customize.inspector"] to { PropertyInspector({ selection }, DefaultConfig.style) },
        Dict["Cutting Panel", "ui.customize.cuttingPanel"] to { CuttingView(DefaultConfig.style) },
        Dict["Timeline", "ui.customize.timeline"] to { TimelinePanel(DefaultConfig.style) },
        Dict["Animations", "ui.customize.graphEditor"] to { GraphEditor(DefaultConfig.style) },
        Dict["Files", "ui.customize.fileExplorer"] to { FileExplorer(DefaultConfig.style) }
    ).map { Type(it.first, it.second) }.toMutableList()

    val library = UITypeLibrary(typeList)

}