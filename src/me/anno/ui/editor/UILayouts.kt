package me.anno.ui.editor

import me.anno.config.DefaultConfig
import me.anno.config.DefaultStyle.black
import me.anno.gpu.GFX
import me.anno.gpu.GFX.ask
import me.anno.gpu.GFX.openMenu
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
import me.anno.studio.RemsStudio.workspace
import me.anno.studio.Studio
import me.anno.studio.Studio.nullCamera
import me.anno.studio.Studio.project
import me.anno.studio.Studio.root
import me.anno.studio.Studio.targetDuration
import me.anno.studio.Studio.targetFPS
import me.anno.studio.Studio.targetHeight
import me.anno.studio.Studio.targetOutputFile
import me.anno.studio.Studio.targetWidth
import me.anno.ui.base.*
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
import me.anno.ui.editor.files.toAllowedFilename
import me.anno.ui.editor.graphs.GraphEditor
import me.anno.ui.editor.sceneTabs.SceneTabs
import me.anno.ui.editor.sceneView.ScenePreview
import me.anno.ui.editor.sceneView.SceneView
import me.anno.ui.editor.treeView.TreeView
import me.anno.ui.input.FileInput
import me.anno.ui.input.TextInput
import me.anno.ui.style.Style
import me.anno.video.VideoAudioCreator
import me.anno.video.VideoCreator
import org.apache.logging.log4j.LogManager
import java.io.File
import kotlin.concurrent.thread
import kotlin.contracts.contract
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

        // manage and load recent projects
        // load recently opened parts / scenes / default scene
        // list of all known projects
        // color them depending on existence

        val dir = "directory" // vs folder ^^
        val style = DefaultConfig.style
        val welcome = PanelListY(style)
        val title = TextPanel("Rem's Studio", style)
        title.textSize *= 3
        welcome += title

        welcome += SpacePanel(0, 1, style)

        fun openProject(name: String, file: File){
            thread {
                RemsStudio.loadProject(name.trim(), file)
                Studio.addEvent {
                    nullCamera.farZ.set(5000f)
                    windowStack.clear()
                    createEditorUI()
                }
                DefaultConfig.addToRecentProjects(project!!)
            }
        }

        val recentProjects = PanelListY(style)
        welcome += recentProjects
        for (project in DefaultConfig.getRecentProjects()) {
            val tp = object : TextPanel(project.name, style) {
                override val enableHoverColor = true
            }
            tp.setTooltip(project.file.absolutePath)
            thread {// file search can use some time
                if(!project.file.exists()){
                    tp.textColor = 0xff0000 or black
                    tp.setTooltip("${project.file.absolutePath}, not found!")
                }
            }
            tp.setOnClickListener { _, _, button, _ ->
                fun open(){// open zip?
                    if(project.file.exists() && project.file.isDirectory){
                        openProject(project.name, project.file)
                    } else {
                        openMenu(listOf(
                            "File not found!" to {}
                        ))
                    }
                }
                when {
                    button.isLeft -> open()
                    button.isRight -> {
                        openMenu(listOf(
                            "Open" to { open() },
                            "Hide" to {
                                DefaultConfig.removeFromRecentProjects(project.file)
                                tp.visibility = Visibility.GONE
                            },
                            "Delete" to {
                                ask("Are you sure?"){
                                    DefaultConfig.removeFromRecentProjects(project.file)
                                    project.file.deleteRecursively()
                                    tp.visibility = Visibility.GONE
                                }
                            }
                        ))
                    }
                }
            }
            tp.padding.top--
            tp.padding.bottom--
            welcome += tp
        }

        welcome += SpacePanel(0, 1, style)

        val nameInput = TextInput("Title", style, "New Project")
        var lastName = nameInput.text

        val fileInput = FileInput("Project Location", style, File(workspace, nameInput.text))

        var usableFile: File? = null

        fun updateFileInputColor() {
            fun rootIsOk(file: File): Boolean {
                if (file.exists()) return true
                return rootIsOk(file.parentFile ?: return false)
            }
            var invalidName = ""
            fun fileNameIsOk(file: File): Boolean {
                if(file.name.isEmpty() && file.parentFile == null) return true // root drive
                if(file.name.toAllowedFilename() != file.name){
                    invalidName = file.name
                    return false
                }
                return fileNameIsOk(file.parentFile ?: return true)
            }

            // todo check if all file name parts are valid...
            // todo check if we have write and read access
            val file = File(fileInput.text)
            var state = 0
            var msg = ""
            when {
                !rootIsOk(file) -> {
                    state = -2
                    msg = "Root $dir does not exist!"
                }
                !file.parentFile.exists() -> {
                    state = -1
                    msg = "Parent $dir does not exist!"
                }
                !fileNameIsOk(file) -> {
                    state = -2
                    msg = "Invalid file name \"$invalidName\""
                }
                file.exists() && file.list()?.isNotEmpty() == true -> {
                    state = -1
                    msg = "Folder is not empty!"
                }
            }
            fileInput.tooltip = msg
            val base = fileInput.base
            base.textColor = when (state) {
                -1 -> 0xffff00
                -2 -> 0xff0000
                else -> 0x00ff00
            } or black
            usableFile = if(state == -2){
                null
            } else file
            base.focusTextColor = base.textColor
        }

        updateFileInputColor()

        nameInput.setChangeListener {
            val newName = if(it.isBlank()) "-" else it.trim()
            if (lastName == fileInput.file.name) {
                fileInput.setText(File(fileInput.file.parentFile, newName).toString(), false)
                updateFileInputColor()
            }
            lastName = newName
        }
        welcome += nameInput

        fileInput.setChangeListener {
            updateFileInputColor()
        }
        welcome += fileInput

        fun loadNewProject() {
            val file = usableFile
            if(file != null){
                openProject(nameInput.text, file)
            } else {
                openMenu("Please choose a $dir!", listOf(
                    "Ok" to {}
                ))
            }
        }

        val button = ButtonPanel("Create Project", style)
        button.setSimpleClickListener {
            loadNewProject()
        }
        welcome += button

        val scroll = ScrollPanelY(welcome, Padding(5), style)
        scroll += WrapAlign.Center

        val background = ScenePreview(style)

        val background2 = PanelListX(style)
        background2.backgroundColor = 0x77777777

        // todo some kind of colored border?
        windowStack.push(Window(background, true, 0, 0))
        windowStack.push(Window(background2, false, 0, 0))
        val mainWindow = Window(scroll, false, 0, 0)
        mainWindow.acceptsClickAway = {
            if (it.isLeft) {
                loadNewProject()
                usableFile != null
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
        ui += SpacePanel(0, 1, style)

        val project = project!!
        project.loadUI()

        ui += project.mainUI as Panel

        ui += SpacePanel(0, 1, style)

        val console = ConsoleOutputPanel(style.getChild("small"))
        // console.fontName = "Consolas"

        RemsStudio.console = console
        console.setTooltip("Double-click to open history")
        console.instantTextLoading = true
        console.text = RemsStudio.lastConsoleLines.lastOrNull() ?: ""
        // console.visibility = Visibility.GONE

        ui += console

        windowStack.clear()
        windowStack += Window(ui, true, 0, 0)

    }

    fun createDefaultMainUI(style: Style): Panel {

        val customUI = CustomListY(style)
        customUI.setWeight(10f)

        val animationWindow = CustomListX(style)
        customUI.add(animationWindow, 2f)

        val treeFiles = CustomListY(style)
        treeFiles += CustomContainer(TreeView(style), style)
        treeFiles += CustomContainer(FileExplorer(style), style)
        animationWindow.add(CustomContainer(treeFiles, style), 0.5f)
        animationWindow.add(CustomContainer(SceneView(style), style), 2f)
        animationWindow.add(CustomContainer(PropertyInspector(style), style), 0.5f)
        animationWindow.setWeight(1f)

        val timeline = GraphEditor(style)
        customUI.add(CustomContainer(timeline, style), 0.5f)

        val linear = CuttingView(style)
        customUI.add(CustomContainer(linear, style), 0.5f)

        return customUI

    }

    fun printLayout() {
        println("Layout:")
        for (window1 in GFX.windowStack) {
            window1.panel.printLayout(1)
        }
    }

}