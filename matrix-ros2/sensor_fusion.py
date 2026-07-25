#!/usr/bin/env python3
"""
sensor_fusion.py — Multi-sensor fusion for ROS2-Matrix integration.

Combines LIDAR, camera, and IMU data into a unified feature vector
for the Matrix agent loop.

Usage:
    ros2 run matrix_ros2 sensor_fusion
"""

import rclpy
from rclpy.node import Node
from rclpy.qos import QoSProfile, ReliabilityPolicy
from sensor_msgs.msg import LaserScan, Image, Imu
from std_msgs.msg import String
import json
import numpy as np
from typing import Dict, Any, Optional


class SensorFusion(Node):
    """Fuses multiple sensor inputs into unified representation."""
    
    def __init__(self):
        super().__init__('sensor_fusion')
        
        # QoS
        qos = QoSProfile(depth=5, reliability=ReliabilityPolicy.BEST_EFFORT)
        
        # Subscribers
        self.lidar_sub = self.create_subscription(
            LaserScan, '/scan', self.lidar_callback, qos)
        self.imu_sub = self.create_subscription(
            Imu, '/imu', self.imu_callback, qos)
        
        # Publisher
        self.fused_pub = self.create_publisher(String, '/matrix/sensors', 10)
        
        # State
        self.lidar_data: Optional[Dict] = None
        self.imu_data: Optional[Dict] = None
        
        # Timer for fusion
        self.timer = self.create_timer(0.1, self.fuse_and_publish)  # 10 Hz
        
        self.get_logger().info('SensorFusion initialized')
    
    def lidar_callback(self, msg: LaserScan):
        """Process LIDAR scan data."""
        ranges = list(msg.ranges)
        
        # Extract features
        valid_ranges = [r for r in ranges if msg.range_min < r < msg.range_max]
        
        if valid_ranges:
            self.lidar_data = {
                'type': 'lidar',
                'min_range': min(valid_ranges),
                'max_range': max(valid_ranges),
                'mean_range': sum(valid_ranges) / len(valid_ranges),
                'num_readings': len(valid_ranges),
                'angle_min': msg.angle_min,
                'angle_max': msg.angle_max,
                # Sector-based features (front, left, right, back)
                'front': self._sector_average(ranges, 0, len(ranges)//4),
                'left': self._sector_average(ranges, len(ranges)//4, len(ranges)//2),
                'right': self._sector_average(ranges, 3*len(ranges)//4, len(ranges)),
                'back': self._sector_average(ranges, len(ranges)//2, 3*len(ranges)//4)
            }
    
    def imu_callback(self, msg: Imu):
        """Process IMU data."""
        self.imu_data = {
            'type': 'imu',
            'linear_accel_x': msg.linear_acceleration.x,
            'linear_accel_y': msg.linear_acceleration.y,
            'linear_accel_z': msg.linear_acceleration.z,
            'angular_vel_z': msg.angular_velocity.z,
            'orientation_z': msg.orientation.z
        }
    
    def _sector_average(self, ranges, start, end):
        """Calculate average range for a sector."""
        sector = ranges[start:end]
        valid = [r for r in sector if 0.0 < r < 100.0]
        return sum(valid) / len(valid) if valid else 100.0
    
    def fuse_and_publish(self):
        """Fuse sensor data and publish unified representation."""
        fused = {}
        
        if self.lidar_data:
            fused.update(self.lidar_data)
        
        if self.imu_data:
            fused.update(self.imu_data)
        
        if fused:
            msg = String()
            msg.data = json.dumps(fused)
            self.fused_pub.publish(msg)


def main(args=None):
    rclpy.init(args=args)
    node = SensorFusion()
    
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.shutdown()


if __name__ == '__main__':
    main()
