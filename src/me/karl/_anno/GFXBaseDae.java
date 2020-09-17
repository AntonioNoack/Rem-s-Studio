/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package me.karl._anno;

import me.anno.gpu.GFX;
import me.anno.gpu.GFXBase0;
import me.karl.main.GeneralSettings;
import me.karl.main.SceneLoader;
import me.karl.renderEngine.RenderEngine;
import me.karl.scene.Scene;
import org.lwjgl.glfw.*;

import static org.lwjgl.glfw.GLFW.*;

/**
 * the main class for the .dae test
 */
public class GFXBaseDae extends GFXBase0 {

    public GFXBaseDae(){
        GFX.INSTANCE.setGameInit(() -> null);
        GFX.INSTANCE.setShutdown(() -> null);
        title += ": .dea Test";
    }

    RenderEngine engine;
    Scene scene;

    @Override
    public void addCallbacks() {
        super.addCallbacks();
        glfwSetMouseButtonCallback(window, new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                switch (action) {
                    case GLFW_PRESS:
                        Mouse.downKeys |= 1 << button;
                        break;
                    case GLFW_RELEASE:
                        Mouse.downKeys &= ~(1 << button);
                        break;
                    default:
                        // idc
                }
            }
        });
        glfwSetCursorPosCallback(window, new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xpos, double ypos) {
                Mouse.dx = (int) ypos - Mouse.lastX;
                Mouse.dy = (int) xpos - Mouse.lastY;
                Mouse.lastX = (int) xpos;
                Mouse.lastY = (int) ypos;
            }
        });
    }

    @Override
    public void runRenderLoop() {
        super.runRenderLoop();
        engine.close();
    }

    @Override
    public void renderStep0() {
        engine = RenderEngine.init();
        scene = SceneLoader.loadScene(GeneralSettings.RES_FOLDER);
    }

    @Override
    public void renderStep() {
        scene.getCamera().move();
        scene.getAnimatedModel().update(System.currentTimeMillis() * 1e-3);
        engine.renderScene(scene);
    }

    public static void main(String[] args) {
        new GFXBaseDae().run();
    }

}