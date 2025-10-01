package xyz.theforks;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import xyz.theforks.nodes.InterlaceMagNode;
import xyz.theforks.ui.Theme;

public class CalibrationViewer {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private final Group root = new Group();
    private final Xform world = new Xform();
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final Xform cameraXform = new Xform();
    private final Xform cameraXform2 = new Xform();
    private final Xform cameraXform3 = new Xform();
    private double mousePosX, mousePosY;
    private double mouseOldX, mouseOldY;
    private final double CAMERA_INITIAL_DISTANCE = -1000;
    private final double CAMERA_NEAR_CLIP = 0.1;
    private final double CAMERA_FAR_CLIP = 10000.0;
    private static final double MOUSE_SPEED = 0.1;
    private static final double ROTATION_SPEED = 2.0;
    private static final double TRACK_SPEED = 0.3;

    public void show(InterlaceMagNode node, String title) {
        Stage stage = new Stage();
        stage.setTitle(title);

        // Enable depth buffer
        root.getChildren().add(world);
        Scene scene = new Scene(root, WIDTH, HEIGHT, true, SceneAntialiasing.BALANCED);
        Theme.applyDark(scene);
        scene.setCamera(camera);

        // Add ambient light
        AmbientLight light = new AmbientLight(Color.WHITE);
        root.getChildren().add(light);

        // Setup camera transform hierarchy
        root.getChildren().add(cameraXform);
        cameraXform.getChildren().add(cameraXform2);
        cameraXform2.getChildren().add(cameraXform3);
        cameraXform3.getChildren().add(camera);

        // Camera initial position
        camera.setNearClip(CAMERA_NEAR_CLIP);
        camera.setFarClip(CAMERA_FAR_CLIP);
        camera.setTranslateZ(CAMERA_INITIAL_DISTANCE);
        cameraXform.ry.setAngle(320.0);
        cameraXform.rx.setAngle(40.0);

        // Add coordinate axes
        addCoordinateAxes();

        // Calculate scale factor and center point
        List<double[]> calibrationData = node.getCalibrationData();
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE};
        
        for (double[] point : calibrationData) {
            for (int i = 0; i < 3; i++) {
                min[i] = Math.min(min[i], point[i]);
                max[i] = Math.max(max[i], point[i]);
            }
        }
        
        double[] center = {
            (max[0] + min[0]) / 2,
            (max[1] + min[1]) / 2,
            (max[2] + min[2]) / 2
        };
        
        double scale = 200.0 / Math.max(
            max[0] - min[0],
            Math.max(max[1] - min[1], max[2] - min[2])
        );

        // Add scaled and centered points
        for (double[] point : calibrationData) {
            Sphere sphere = new Sphere(2);
            sphere.setTranslateX((point[0] - center[0]) * scale);
            sphere.setTranslateY((point[1] - center[1]) * scale);
            sphere.setTranslateZ((point[2] - center[2]) * scale);
            PhongMaterial material = new PhongMaterial(Color.RED);
            sphere.setMaterial(material);
            world.getChildren().add(sphere);
        }

        // Add curve points
        for (double t = 0; t <= 1.0; t += 0.01) {
            double x = node.getSplineX().value(t);
            double y = node.getSplineY().value(t);
            double z = node.getSplineZ().value(t);
            
            Sphere sphere = new Sphere(1);
            sphere.setTranslateX((x - center[0]) * scale);
            sphere.setTranslateY((y - center[1]) * scale);
            sphere.setTranslateZ((z - center[2]) * scale);
            PhongMaterial material = new PhongMaterial(Color.BLUE);
            sphere.setMaterial(material);
            world.getChildren().add(sphere);
        }

        // Add spline curve
        addSplineCurve(node, center, scale);

        // Initial camera rotation for better view
        cameraXform.rx.setAngle(20);
        cameraXform.ry.setAngle(20);

        // Mouse handlers
        scene.setOnMousePressed(me -> {
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            mouseOldX = me.getSceneX();
            mouseOldY = me.getSceneY();
        });

        scene.setOnMouseDragged(me -> {
            mouseOldX = mousePosX;
            mouseOldY = mousePosY;
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            double mouseDeltaX = (mousePosX - mouseOldX);
            double mouseDeltaY = (mousePosY - mouseOldY);

            if (me.isPrimaryButtonDown()) {
                // Rotation around world
                cameraXform.ry.setAngle(cameraXform.ry.getAngle() + mouseDeltaX * ROTATION_SPEED);
                cameraXform.rx.setAngle(cameraXform.rx.getAngle() - mouseDeltaY * ROTATION_SPEED);
            }
            else if (me.isSecondaryButtonDown()) {
                // Pan in camera's XY plane
                cameraXform2.t.setX(cameraXform2.t.getX() + mouseDeltaX * TRACK_SPEED);
                cameraXform2.t.setY(cameraXform2.t.getY() + mouseDeltaY * TRACK_SPEED);
            }
        });

        scene.setOnScroll(se -> {
            // Zoom along camera Z axis
            double delta = se.getDeltaY() * TRACK_SPEED;
            cameraXform2.t.setZ(cameraXform2.t.getZ() + delta);
            //System.out.println("Zoom: " + delta);
        });

        // Initial camera position
        cameraXform2.t.setX(0.0);
        cameraXform2.t.setY(0.0);
        camera.setTranslateZ(CAMERA_INITIAL_DISTANCE);
        cameraXform.ry.setAngle(320.0);
        cameraXform.rx.setAngle(40.0);

        stage.setScene(scene);
        stage.show();
    }

    // Add this method to CalibrationViewer class
    private void addSplineCurve(InterlaceMagNode node, double[] center, double scale) {
        // Create a group for the curve segments
        Group curveGroup = new Group();
        
        // Sample points along the curve
        List<Point3D> points = new ArrayList<>();
        for (double t = 0; t <= 1.0; t += 0.005) { // Smaller step for smoother curve
            double x = (node.getSplineX().value(t) - center[0]) * scale;
            double y = (node.getSplineY().value(t) - center[1]) * scale;
            double z = (node.getSplineZ().value(t) - center[2]) * scale;
            points.add(new Point3D(x, y, z));
        }

        // Create line segments between points
        for (int i = 0; i < points.size() - 1; i++) {
            Point3D p1 = points.get(i);
            Point3D p2 = points.get(i + 1);
            
            Cylinder line = createCylinderBetweenPoints(p1, p2, 0.5); // 0.5 is line thickness
            PhongMaterial material = new PhongMaterial(Color.BLUE);
            line.setMaterial(material);
            curveGroup.getChildren().add(line);
        }

        world.getChildren().add(curveGroup);
    }

    // Helper method to create a cylinder between two 3D points
    private Cylinder createCylinderBetweenPoints(Point3D p1, Point3D p2, double radius) {
        Point3D diff = p2.subtract(p1);
        double height = diff.magnitude();
        
        Point3D mid = p2.add(p1).multiply(0.5);
        
        Cylinder line = new Cylinder(radius, height);
        line.setTranslateX(mid.getX());
        line.setTranslateY(mid.getY());
        line.setTranslateZ(mid.getZ());
        
        // Find rotation axis and angle
        Point3D axisOfRotation = new Point3D(0, 1, 0).crossProduct(diff);
        double angle = Math.acos(diff.normalize().getY());
        
        // Create and apply rotation transform
        Rotate rxRotate = new Rotate();
        rxRotate.setAxis(axisOfRotation);
        rxRotate.setAngle(Math.toDegrees(angle));
        line.getTransforms().add(rxRotate);
        
        return line;
    }

    private void addCoordinateAxes() {
        Group axisGroup = new Group();
        double axisLength = 200.0;
        double axisWidth = 1.0;
        
        // X axis (red)
        Cylinder xAxis = new Cylinder(axisWidth, axisLength);
        xAxis.setMaterial(new PhongMaterial(Color.RED));
        xAxis.setRotate(90);
        xAxis.setTranslateX(axisLength/2);
        
        // Y axis (green)
        Cylinder yAxis = new Cylinder(axisWidth, axisLength);
        yAxis.setMaterial(new PhongMaterial(Color.GREEN));
        yAxis.setTranslateY(axisLength/2);
        
        // Z axis (blue)
        Cylinder zAxis = new Cylinder(axisWidth, axisLength);
        zAxis.setMaterial(new PhongMaterial(Color.BLUE));
        zAxis.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
        zAxis.setTranslateZ(axisLength/2);
        
        // Arrow heads
        double coneHeight = 10;
        double coneRadius = 3;
        
        Cylinder xCone = new Cylinder(coneRadius, coneHeight);
        xCone.setMaterial(new PhongMaterial(Color.RED));
        xCone.setRotate(90);
        xCone.setTranslateX(axisLength);
        
        Cylinder yCone = new Cylinder(coneRadius, coneHeight);
        yCone.setMaterial(new PhongMaterial(Color.GREEN));
        yCone.setTranslateY(axisLength);
        
        Cylinder zCone = new Cylinder(coneRadius, coneHeight);
        zCone.setMaterial(new PhongMaterial(Color.BLUE));
        zCone.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
        zCone.setTranslateZ(axisLength);
        
        axisGroup.getChildren().addAll(xAxis, yAxis, zAxis, xCone, yCone, zCone);
        world.getChildren().add(axisGroup);
    }
}

// Updated Xform class
class Xform extends Group {
    public final Rotate rx = new Rotate(0, Rotate.X_AXIS);
    public final Rotate ry = new Rotate(0, Rotate.Y_AXIS);
    public final Rotate rz = new Rotate(0, Rotate.Z_AXIS);
    public final Translate t = new Translate();
    
    public Xform() {
        super();
        getTransforms().addAll(t, rx, ry, rz);
    }

    public void setRotateZ(double angle) {
        rz.setAngle(angle);
    }
}