# ROS2 Integration — Implementation Plan

**Status:** 🔧 PROTOTYPE (Python bridge only)
**Priority:** MEDIUM
**Estimated effort:** 3-4 weeks
**Target:** v3.59

---

## Problem Statement

Current ROS2 integration is a simple Python script (ros2_bridge.py). Need:
1. Full ROS2 node integration
2. Sensor fusion (LIDAR, camera, IMU)
3. Real-time control loop
4. Hardware-in-the-loop testing

---

## Implementation Steps

### Step 1: ROS2 Node Architecture (Week 1)
```python
# matrix-ros2/matrix_node.py
import rclpy
from rclpy.node import Node
from sensor_msgs.msg import LaserScan, Image, Imu
from geometry_msgs.msg import Twist
from std_msgs.msg import String
import json

class MatrixNode(Node):
    def __init__(self):
        super().__init__('matrix_node')
        
        # Subscribers
        self.lidar_sub = self.create_subscription(
            LaserScan, '/scan', self.lidar_callback, 10)
        self.camera_sub = self.create_subscription(
            Image, '/camera/image_raw', self.camera_callback, 10)
        self.imu_sub = self.create_subscription(
            Imu, '/imu', self.imu_callback, 10)
        
        # Publishers
        self.cmd_pub = self.create_publisher(Twist, '/cmd_vel', 10)
        self.status_pub = self.create_publisher(String, '/matrix/status', 10)
        
        # Matrix connection
        self.matrix_url = 'http://matrix-core:9091'
        self.agent_id = None
        
    def lidar_callback(self, msg):
        """Process LIDAR data through Matrix"""
        sensor_data = {
            'type': 'lidar',
            'ranges': list(msg.ranges),
            'angle_min': msg.angle_min,
            'angle_max': msg.angle_max
        }
        action = self.query_matrix(sensor_data)
        self.execute_action(action)
    
    def camera_callback(self, msg):
        """Process camera data through Matrix"""
        sensor_data = {
            'type': 'camera',
            'width': msg.width,
            'height': msg.height,
            'encoding': msg.encoding,
            'data': list(msg.data[:1000])  # Sample
        }
        action = self.query_matrix(sensor_data)
        self.execute_action(action)
    
    def query_matrix(self, sensor_data):
        """Query Matrix core for action"""
        import requests
        response = requests.post(
            f'{self.matrix_url}/api/v1/agent/infer',
            json={'sensors': [sensor_data], 'agent_id': self.agent_id}
        )
        return response.json()
```

### Step 2: Sensor Fusion (Week 2)
```python
# matrix-ros2/sensor_fusion.py
class SensorFusion:
    def __init__(self):
        self.lidar_buffer = []
        self.camera_buffer = []
        self.imu_buffer = []
    
    def fuse(self, lidar=None, camera=None, imu=None):
        """Fuse multiple sensor inputs into single representation"""
        features = []
        
        if lidar:
            # Extract features from LIDAR
            min_range = min(lidar['ranges'])
            max_range = max(lidar['ranges'])
            features.extend([min_range, max_range, len(lidar['ranges'])])
        
        if camera:
            # Extract features from camera (simplified)
            brightness = sum(camera['data']) / len(camera['data'])
            features.append(brightness)
        
        if imu:
            # Extract features from IMU
            features.extend([
                imu['linear_acceleration']['x'],
                imu['linear_acceleration']['y'],
                imu['angular_velocity']['z']
            ])
        
        return features
```

### Step 3: Real-Time Control Loop (Week 2)
```python
# matrix-ros2/control_loop.py
class ControlLoop:
    def __init__(self, node, rate_hz=10):
        self.node = node
        self.rate = node.create_rate(rate_hz)
        self.running = False
    
    def start(self):
        self.running = True
        while self.running and rclpy.ok():
            # Collect sensor data
            sensors = self.collect_sensors()
            
            # Query Matrix
            action = self.node.query_matrix(sensors)
            
            # Execute action
            self.execute_action(action)
            
            # Sleep
            self.rate.sleep()
    
    def execute_action(self, action):
        """Convert Matrix action to ROS2 command"""
        cmd = Twist()
        
        if action.get('action') == 'MOVE_FORWARD':
            cmd.linear.x = 0.5
        elif action.get('action') == 'TURN_LEFT':
            cmd.angular.z = 0.5
        elif action.get('action') == 'TURN_RIGHT':
            cmd.angular.z = -0.5
        elif action.get('action') == 'STOP':
            pass  # All zeros
        
        self.node.cmd_pub.publish(cmd)
```

### Step 4: Launch Files (Week 3)
```xml
<!-- matrix-ros2/launch/matrix_robot.launch.py -->
from launch import LaunchDescription
from launch_ros.actions import Node

def generate_launch_description():
    return LaunchDescription([
        Node(
            package='matrix_ros2',
            executable='matrix_node',
            name='matrix_node',
            parameters=[{
                'matrix_url': 'http://matrix-core:9091',
                'rate_hz': 10
            }]
        ),
        Node(
            package='matrix_ros2',
            executable='sensor_fusion',
            name='sensor_fusion'
        )
    ])
```

### Step 5: Docker Integration (Week 3)
```dockerfile
# matrix-ros2/Dockerfile
FROM ros:humble

RUN apt-get update && apt-get install -y \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip3 install -r requirements.txt

COPY . /workspace/matrix-ros2
WORKDIR /workspace/matrix-ros2

CMD ["ros2", "launch", "matrix_ros2", "matrix_robot.launch.py"]
```

### Step 6: Simulation Testing (Week 4)
```bash
# Launch Gazebo simulation
ros2 launch gazebo_ros empty_world.launch.py

# Launch Matrix node
ros2 launch matrix_ros2 matrix_robot.launch.py

# Send test commands
ros2 topic echo /cmd_vel
```

---

## Supported Robots

| Robot | Type | Sensors | Actuators |
|-------|------|---------|-----------|
| TurtleBot3 | Mobile | LIDAR, IMU | Wheels |
| UR5e | Arm | Force/Torque | Joints |
| PX4 | Drone | IMU, GPS, Camera | Motors |

---

## Verification

```bash
# Build
colcon build --packages-select matrix_ros2

# Test
ros2 launch matrix_ros2 matrix_robot.launch.py

# Verify
ros2 topic list
ros2 topic echo /matrix/status
```
