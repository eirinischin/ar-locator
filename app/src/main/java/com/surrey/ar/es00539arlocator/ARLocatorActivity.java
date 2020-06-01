/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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
package com.surrey.ar.es00539arlocator;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
 */
public class ARLocatorActivity extends AppCompatActivity {
  private static final String TAG = ARLocatorActivity.class.getSimpleName();
  private static final double MIN_OPENGL_VERSION = 3.0;

  private ArFragment arFragment;
  private ModelRenderable currentRenderable;
  private ModelRenderable keysRenderable;
  private ModelRenderable oculosRenderable;
  private ModelRenderable earthRenderable;
  private ModelRenderable triangleRenderable;
  private AnchorNode keysNode;
  private AnchorNode oculosNode;
  private AnchorNode earthNode;
  private Node triangleNode;
  private ImageButton modelButton;
  private ImageButton mapButton;
  private TextView locationView;
  private OvermapView overmapView;
  private Session session;
  private boolean installRequested;
  private boolean shouldConfigureSession;
  private ArSceneView arSceneView;
  private String augmented_keys;
  private String augmented_images_earth;
  private boolean location_known;

  protected void addModel(Consumer<ModelRenderable> im, int resource) {
      ModelRenderable.builder()
              .setSource(this, resource)
              .build()
              .thenAccept(im)
              .exceptionally(
                      throwable -> {
                          Toast toast =
                                  Toast.makeText(this, "Unable to load renderable", Toast.LENGTH_LONG);
                          toast.setGravity(Gravity.CENTER, 0, 0);
                          toast.show();
                          return null;
                      });
  }

  private void addAugmentedImage(AugmentedImageDatabase augmentedImageDatabase, int resource) {
        augmentedImageDatabase.addImage(
                getResources().getResourceEntryName(resource),
                BitmapFactory.decodeResource(getResources(), resource));
  }

  @Override
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  // CompletableFuture requires api level 24
  // FutureReturnValueIgnored is not valid
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.e(TAG, "Initializing AR Locator");

    installRequested = false;
    location_known = false;

    if (!checkIsSupportedDeviceOrFinish(this)) {
      return;
    }

    setContentView(R.layout.activity_ux);

    addModel(renderable -> {
        oculosRenderable = renderable;
        currentRenderable = renderable;
    }, R.raw.oculos);

    addModel(renderable -> {
        keysRenderable = renderable;
    }, R.raw.keys);

    addModel(renderable -> {
        earthRenderable = renderable;
    }, R.raw.earth);

    triangleNode = new Node();
    addModel(renderable -> {
        triangleRenderable = renderable;
        triangleNode.setParent(arFragment.getArSceneView().getScene().getCamera());
        triangleNode.setLocalPosition(new Vector3(0f,-0.1f,-0.2f));
        triangleNode.setRenderable(renderable);
    }, R.raw.triangle);



    overmapView = findViewById(R.id.overmapView);
    modelButton = findViewById(R.id.modelButton);
    mapButton = findViewById(R.id.mapButton);
    locationView = findViewById(R.id.locationView);
    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
    arSceneView = arFragment.getArSceneView();
    augmented_keys = getResources().getResourceEntryName(R.drawable.augmented_keys);
    augmented_images_earth = getResources().getResourceEntryName(R.drawable.augmented_images_earth);

    // ##### Set Origin point for map #####
    overmapView.setOrigin(298, 250);

    locationView.setText("Location: Unknown");

    mapButton.setVisibility(View.GONE);
    mapButton.setOnClickListener((View v) -> {
        if (overmapView.getVisibility() == View.VISIBLE) {
            overmapView.setVisibility(View.GONE);
        } else {
            overmapView.setVisibility(View.VISIBLE);
        }
    });

    modelButton.setOnClickListener((View v) -> {
        if (currentRenderable == oculosRenderable) {
            currentRenderable = keysRenderable;
            modelButton.setImageResource(R.drawable.preview_keys);
        } else if (currentRenderable == keysRenderable) {
            currentRenderable = oculosRenderable;
            modelButton.setImageResource(R.drawable.preview_glasses);
        }
    });

    initializeSceneView();

    arFragment.setOnTapArPlaneListener(
        (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
          if (currentRenderable == null) {
            return;
          }
          Anchor anchor = hitResult.createAnchor();
          if (currentRenderable == oculosRenderable) {
              oculosNode = addNode(anchor, oculosNode, oculosRenderable);
          } else if (currentRenderable == keysRenderable) {
              keysNode = addNode(anchor, keysNode, keysRenderable);
          }
        });
  }

    private AnchorNode addNode(Anchor anchor, AnchorNode currentNode, ModelRenderable renderable) {
        // Detach if it exists
        if (currentNode != null) {
            currentNode.getAnchor().detach();
            currentNode.setParent(null);
        }

        // Create the Anchor.
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arSceneView.getScene());

        // Create the transformable and add it to the anchor.
        TransformableNode node = new TransformableNode(arFragment.getTransformationSystem());
        node.setParent(anchorNode);
        node.setRenderable(renderable);
        node.select();

        return anchorNode;
    }
    private void initializeSceneView() {
        arSceneView.getScene().addOnUpdateListener(this::onUpdateFrame);
    }

    private void pointTriangle(AnchorNode node) {
        Vector3 trianglePosition = triangleNode.getWorldPosition();
        Vector3 nodePosition = node.getWorldPosition();
        Vector3 direction = Vector3.subtract(trianglePosition, nodePosition);
        Quaternion lookRotation = Quaternion.lookRotation(direction, Vector3.up());
        triangleNode.setWorldRotation(lookRotation);
        triangleNode.setEnabled(true);
    }

    public static Vector3 getRelativePosition(Vector3 origin, Vector3 position) {
        Vector3 distance = Vector3.subtract(position, origin);
        Vector3 relativePosition = new Vector3();
        relativePosition.x = Vector3.dot(distance, Vector3.right());
        relativePosition.y = Vector3.dot(distance, Vector3.up());
        relativePosition.z = Vector3.dot(distance, Vector3.forward());

        return relativePosition;
    }

    private void onUpdateFrame(FrameTime frameTime) {
        Frame frame = arSceneView.getArFrame();
        Collection<AugmentedImage> updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage.class);

        if (triangleNode != null) {
            if (currentRenderable == keysRenderable && keysNode != null) {
                pointTriangle(keysNode);
            } else if (currentRenderable == oculosRenderable && oculosNode != null) {
                pointTriangle(oculosNode);
            } else {
                triangleNode.setEnabled(false);
            }
        }

        if (overmapView.getVisibility() == View.VISIBLE) {
            // Draw the map since its visible
            //390, 250
            Vector3 earth = earthNode.getWorldPosition();
            Vector3 camera = arSceneView.getScene().getCamera().getWorldPosition();
            Vector3 relative = getRelativePosition(earth, camera);
            overmapView.setCamera(relative);
            //Log.e(TAG, "Camera Relative: " + relative);

            if (keysNode != null) {
                Vector3 keys = keysNode.getWorldPosition();
                Vector3 keysRelative = getRelativePosition(earth, keys);
                overmapView.setKeys(keysRelative);
                //Log.e(TAG, "Keys Relative: " + keysRelative);
            }
            if (oculosNode != null) {
                Vector3 oculos = oculosNode.getWorldPosition();
                Vector3 oculosRelative = getRelativePosition(earth, oculos);
                overmapView.setOculos(oculosRelative);
                //Log.e(TAG, "Oculos Relative: " + oculosRelative);
            }
        }

        for (AugmentedImage augmentedImage : updatedAugmentedImages) {
            if (augmentedImage.getTrackingState() == TrackingState.TRACKING &&
                    augmentedImage.getTrackingMethod() == AugmentedImage.TrackingMethod.FULL_TRACKING) {

                Anchor anchor = augmentedImage.createAnchor(augmentedImage.getCenterPose());
                String name = augmentedImage.getName();

                if (name.equals(augmented_keys)) {
                    keysNode = addNode(anchor, keysNode, keysRenderable);
                } else if (name.equals(augmented_images_earth)) {
                    earthNode = addNode(anchor, earthNode, earthRenderable);
                    if (!location_known) {
                        locationView.setText("Location: Office");
                        mapButton.setVisibility(View.VISIBLE);
                        location_known = true;
                    }
                }
            }
        }
    }

    private boolean setupAugmentedImageDb(Config config) {
        AugmentedImageDatabase augmentedImageDatabase;
        augmentedImageDatabase = new AugmentedImageDatabase(session);
        addAugmentedImage(augmentedImageDatabase, R.drawable.augmented_images_earth);
        addAugmentedImage(augmentedImageDatabase, R.drawable.augmented_keys);
        config.setAugmentedImageDatabase(augmentedImageDatabase);
        return true;
    }

    private void configureSession() {
        Config config = new Config(session);
        if (!setupAugmentedImageDb(config)) {
           Log.e(TAG, "Could not setup augmented image database");
        }
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        session.configure(config);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                session = new Session(/* context = */ this);
            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (Exception e) {
                message = "This device does not support AR";
                exception = e;
            }

            if (message != null) {
                Log.e(TAG, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }

            shouldConfigureSession = true;
        }

        if (shouldConfigureSession) {
            configureSession();
            shouldConfigureSession = false;
            arSceneView.setupSession(session);
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
            arSceneView.resume();
        } catch (CameraNotAvailableException e) {
            // In some cases (such as another camera app launching) the camera may be given to
            // a different app instead. Handle this properly by showing a message and recreate the
            // session at the next iteration.
            Log.e(TAG, "Camera not available. Please restart the app.");
            session = null;
            return;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            arSceneView.pause();
            session.pause();
        }
    }

  /**
   * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
   * on this device.
   *
   * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
   *
   * <p>Finishes the activity if Sceneform can not run
   */
  public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
    if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
      Log.e(TAG, "Sceneform requires Android N or later");
      Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
      activity.finish();
      return false;
    }
    String openGlVersionString =
        ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
            .getDeviceConfigurationInfo()
            .getGlEsVersion();
    if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
      Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
      Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
          .show();
      activity.finish();
      return false;
    }
    return true;
  }
}
