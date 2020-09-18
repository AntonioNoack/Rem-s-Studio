package me.anno.ui.editor

import me.anno.config.DefaultConfig
import me.anno.gpu.GFX
import me.anno.gpu.GFX.select
import me.anno.gpu.Window
import me.anno.input.Input
import me.anno.objects.Camera
import me.anno.objects.Text
import me.anno.objects.Transform
import me.anno.objects.cache.Cache
import me.anno.objects.rendering.RenderSettings
import me.anno.studio.RemsStudio
import me.anno.studio.RemsStudio.windowStack
import me.anno.studio.Studio
import me.anno.studio.Studio.nullCamera
import me.anno.studio.Studio.project
import me.anno.studio.Studio.root
import me.anno.studio.Studio.targetDuration
import me.anno.studio.Studio.targetFPS
import me.anno.studio.Studio.targetHeight
import me.anno.studio.Studio.targetOutputFile
import me.anno.studio.Studio.targetWidth
import me.anno.ui.base.ButtonPanel
import me.anno.ui.base.SpacePanel
import me.anno.ui.base.TextPanel
import me.anno.ui.base.components.Padding
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.constraints.WrapAlign
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.scrolling.ScrollPanelY
import me.anno.ui.custom.CustomContainer
import me.anno.ui.custom.CustomListX
import me.anno.ui.custom.CustomListY
import me.anno.ui.debug.ConsoleOutputPanel
import me.anno.ui.editor.cutting.CuttingView
import me.anno.ui.editor.files.FileExplorer
import me.anno.ui.editor.graphs.GraphEditor
import me.anno.ui.editor.sceneTabs.SceneTabs
import me.anno.ui.editor.sceneView.ScenePreview
import me.anno.ui.editor.sceneView.SceneView
import me.anno.ui.editor.treeView.TreeView
import me.anno.ui.input.TextInput
import me.anno.video.VideoAudioCreator
import me.anno.video.VideoCreator
import org.apache.logging.log4j.LogManager
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.roundToInt

object UILayouts {

    private val LOGGER = LogManager.getLogger(UILayouts::class)

    fun createLoadingUI() {

        val style = DefaultConfig.style

        val ui = PanelListY(style)
        val customUI = CustomListY(style)
        customUI.setWeight(10f)

        RemsStudio.ui = ui

    }

    fun renderPart(size: Int) {
        render(targetWidth / size, targetHeight / size)
    }

    fun render(width: Int, height: Int) {
        if (width % 2 != 0 || height % 2 != 0) return render(
            width / 2 * 2,
            height / 2 * 2
        )
        LOGGER.info("rendering video at $width x $height")
        val tmpFile = File(
            targetOutputFile.parentFile,
            targetOutputFile.nameWithoutExtension + ".tmp." + targetOutputFile.extension
        )
        val fps = targetFPS
        val totalFrameCount = (fps * targetDuration).toInt()
        val sampleRate = 48000
        VideoAudioCreator(
            VideoCreator(
                width, height,
                targetFPS, totalFrameCount, tmpFile
            ), sampleRate, targetOutputFile
        ).start()
    }

    fun createWelcomeUI() {

        val style = DefaultConfig.style
        val welcome = PanelListY(style)
        val title = TextPanel("Rem's Studio", style)
        title.textSize *= 3
        welcome += title

        welcome += SpacePanel(0, 1, style)

        val recentProjects = PanelListY(style)
        welcome += recentProjects
        for (i in 0 until 5) {
            val tp = object : TextPanel("Project $i", style) {
                override val enableHoverColor = true
            }
            tp.padding.top--
            tp.padding.bottom--
            welcome += tp
        }

        welcome += SpacePanel(0, 1, style)

        val title2 = TextInput("Title", style)
        title2.setText("New Project", true)
        welcome += title2

        fun loadNewProject(){
            thread {
                RemsStudio.loadProject()
                Studio.addEvent {
                    nullCamera.farZ.set(5000f)
                    windowStack.clear()
                    createEditorUI()
                }
            }
        }

        val button = ButtonPanel("Create Project", style)
        button.setSimpleClickListener { loadNewProject() }
        welcome += button

        val scroll = ScrollPanelY(welcome, Padding(5), style)
        scroll += WrapAlign.Center

        val background = ScenePreview(style)

        val background2 = PanelListX(style)
        background2.backgroundColor = 0x77777777

        windowStack.push(Window(background, true, 0, 0))
        windowStack.push(Window(background2, false, 0, 0))
        val mainWindow = Window(scroll, false, 0, 0)
        mainWindow.acceptsClickAway = {
            if(it.isLeft){
                loadNewProject()
                true
            } else false
        }
        windowStack.push(mainWindow)

        Text("Rem's Studio", root).apply {
            blockAlignmentX = AxisAlignment.CENTER
            blockAlignmentY = AxisAlignment.CENTER
            textAlignment = AxisAlignment.CENTER
        }

        nullCamera.farZ.set(100f)

    }

    fun createEditorUI() {

        val style = DefaultConfig.style

        val ui = PanelListY(style)
        val customUI = CustomListY(style)
        customUI.setWeight(10f)

        RemsStudio.ui = ui

        // todo show the file location up there, too?
        // todo fully customizable content
        val options = OptionBar(style)
        // options.addMajor("File")
        // options.addMajor("Edit")
        // options.addMajor("View")
        // options.addMajor("Navigate")
        // options.addMajor("Code")

        options.addAction("File", "Save") { Input.save() }
        options.addAction("File", "Load") { }

        options.addAction("Select", "Render Settings") { select(RenderSettings) }
        options.addAction("Select", "Inspector Camera") { select(nullCamera) }
        options.addAction("Debug", "Refresh (Ctrl+F5)") { Cache.clear() }

        options.addAction("Render", "Set%") {
            render(
                max(2, (project!!.targetWidth * project!!.targetSizePercentage / 100).roundToInt()),
                max(2, (project!!.targetHeight * project!!.targetSizePercentage / 100).roundToInt())
            )
        }
        options.addAction("Render", "Full") { renderPart(1) }
        options.addAction("Render", "Half") { renderPart(2) }
        options.addAction("Render", "Quarter") { renderPart(4) }

        ui += options
        ui += SceneTabs

        // todo load the last opened tabs from the previous project...

        root = Transform(null)
        root.name = "Root"

        // val a = Transform(Vector3f(10f, 50f, 0f), Vector3f(1f,1f,1f), Quaternionf(1f,0f,0f,0f), root)
        // for(i in 0 until 3) Transform(null, null, null, a)
        // val b = Transform(null, null, null, root)
        // for(i in 0 until 2) Transform(null, null, null, b)

        Camera(root)
        /*Circle(root).apply {
            name = "C1"
            color.addKeyframe(0.0, Vector4f(0f, 0f, 0f, 0f))
            color.addKeyframe(0.1, Vector4f(0.5f, 1f, 1f, 1f))
            color.isAnimated = true
        }
        Circle(root).apply {
            name = "C2"
            color.addKeyframe(0.0, Vector4f(0f, 0f, 0f, 0f))
            color.addKeyframe(0.1, Vector4f(1f, 1f, 1f, 1f))
            color.isAnimated = true
        }*/
        // Text("Text", root)
        // Video(File(OS.home, "Videos\\Captures\\Cities_ Skylines 2020-01-06 19-32-23.mp4"), GFX.root)
        // Text("Hi! \uD83D\uDE09", GFX.root)
        // Image(File(OS.downloads, "tiger.svg"), root).position.addKeyframe(0f, Vector3f(0f, 0f, 0.01f), 0.1f)

        val animationWindow = CustomListX(style)
        customUI.add(animationWindow, 200f)

        val treeFiles = CustomListY(style)
        treeFiles += CustomContainer(TreeView(style), style)
        treeFiles += CustomContainer(FileExplorer(style), style)
        animationWindow.add(CustomContainer(treeFiles, style), 50f)
        animationWindow.add(CustomContainer(SceneView(style), style), 200f)
        animationWindow.add(CustomContainer(PropertyInspector(style), style), 50f)
        animationWindow.setWeight(1f)

        val timeline = GraphEditor(style)
        customUI.add(CustomContainer(timeline, style), 50f)

        val linear = CuttingView(style)
        customUI.add(CustomContainer(linear, style), 50f)

        ui += SpacePanel(0, 1, style)
        ui += customUI
        ui += SpacePanel(0, 1, style)

        val console = ConsoleOutputPanel(style.getChild("small"))
        // console.fontName = "Consolas"

        RemsStudio.console = console
        console.setTooltip("Double-click to open history")
        console.instantTextLoading = true
        // console.visibility = Visibility.GONE

        ui += console

        windowStack.clear()
        windowStack += Window(ui, true, 0, 0)

    }

    fun printLayout() {
        println("Layout:")
        for (window1 in GFX.windowStack) {
            window1.panel.printLayout(1)
        }
    }

}