#!/usr/bin/env python3
"""
matrix_node.py — ROS2 node for M.A.T.R.I.X. integration.

Connects to matrix-core via REST API and bridges ROS2 topics
to the Matrix agent loop for autonomous robot control.

Usage:
    ros2 run matrix_ros2 matrix_node --ros-args -p matrix_url:=http://matrix-core:9091
"""

import rclpy
from rclpy.node import Node
from rclpy.qos import QoSProfile, ReliabilityPolicy
from geometry_msgs.msg import Twist
from std_msgs.msg import String
import json
import requests
from typing import Optional, Dict, Any


class MatrixNode(Node):
    """ROS2 node that bridges to M.A.T.R.I.X. core."""
    
    def __init__(self):
        super().__init__('matrix_node')
        
        # Parameters
        self.declare_parameter('matrix_url', 'http://localhost:9091')
        self.declare_parameter('agent_id', 'ros2-agent')
        self.declare_parameter('rate_hz', 10.0)
        self.declare_parameter('timeout_sec', 5.0)
        
        self.matrix_url = self.get_parameter('matrix_url').get_parameter_value().string_value
        self.agent_id = self.get_parameter('agent_id').get_parameter_value().string_value
        self.rate_hz = self.get_parameter('rate_hz').get_parameter_value().double_value
        self.timeout_sec = self.get_parameter('timeout_sec').get_parameter_value().double_value
        
        # QoS profile for reliable delivery
        qos = QoSProfile(depth=10, reliability=ReliabilityPolicy.RELIABLE)
        
        # Publishers
        self.cmd_pub = self.create_publisher(Twist, '/cmd_vel', qos)
        self.status_pub = self.create_publisher(String, '/matrix/status', qos)
        self.action_pub = self.create_publisher(String, '/matrix/action', qos)
        
        # Subscribers
        self.sensor_sub = self.create_subscription(
            String, '/matrix/sensors', self.sensor_callback, qos)
        self.command_sub = self.create_subscription(
            String, '/matrix/commands', self.command_callback, qos)
        
        # State
        self.sensor_data: Dict[str, Any] = {}
        self.connected = False
        self.cycle_count = 0
        
        # Timer for main control loop
        timer_period = 1.0 / self.rate_hz
        self.timer = self.create_timer(timer_period, self.control_loop)
        
        self.get_logger().info(f'MatrixNode initialized: {self.matrix_url}')
        self.get_logger().info(f'Agent ID: {self.agent_id}, Rate: {self.rate_hz} Hz')
    
    def sensor_callback(self, msg: String):
        """Receive sensor data from ROS2 topics."""
        try:
            data = json.loads(msg.data)
            self.sensor_data.update(data)
            self.get_logger().debug(f'Sensor data received: {list(data.keys())}')
        except json.JSONDecodeError as e:
            self.get_logger().warn(f'Invalid sensor JSON: {e}')
    
    def command_callback(self, msg: String):
        """Receive direct commands."""
        try:
            cmd = json.loads(msg.data)
            self.execute_command(cmd)
        except json.JSONDecodeError as e:
            self.get_logger().warn(f'Invalid command JSON: {e}')
    
    def control_loop(self):
        """Main control loop: query Matrix and execute actions."""
        self.cycle_count += 1
        
        if not self.sensor_data:
            return
        
        try:
            # Query Matrix for action
            action = self.query_matrix()
            if action:
                self.execute_action(action)
                
                # Publish action for monitoring
                action_msg = String()
                action_msg.data = json.dumps(action)
                self.action_pub.publish(action_msg)
                
        except requests.RequestException as e:
            if self.cycle_count % 100 == 0:  # Log every 100 cycles
                self.get_logger().warn(f'Matrix connection failed: {e}')
        except Exception as e:
            self.get_logger().error(f'Control loop error: {e}')
    
    def query_matrix(self) -> Optional[Dict[str, Any]]:
        """Query Matrix core for next action."""
        payload = {
            'agent_id': self.agent_id,
            'sensors': [self.sensor_data],
            'cycle': self.cycle_count
        }
        
        response = requests.post(
            f'{self.matrix_url}/api/v1/agent/infer',
            json=payload,
            timeout=self.timeout_sec
        )
        response.raise_for_status()
        return response.json()
    
    def execute_action(self, action: Dict[str, Any]):
        """Convert Matrix action to ROS2 command."""
        cmd = Twist()
        action_type = action.get('action', 'STOP')
        
        if action_type == 'MOVE_FORWARD':
            cmd.linear.x = float(action.get('speed', 0.5))
        elif action_type == 'MOVE_BACKWARD':
            cmd.linear.x = -float(action.get('speed', 0.3))
        elif action_type == 'TURN_LEFT':
            cmd.angular.z = float(action.get('speed', 0.5))
        elif action_type == 'TURN_RIGHT':
            cmd.angular.z = -float(action.get('speed', 0.5))
        elif action_type == 'STOP':
            pass  # All zeros
        elif action_type == 'CUSTOM':
            cmd.linear.x = float(action.get('linear_x', 0.0))
            cmd.linear.y = float(action.get('linear_y', 0.0))
            cmd.angular.z = float(action.get('angular_z', 0.0))
        
        self.cmd_pub.publish(cmd)
        self.get_logger().debug(f'Action: {action_type}')
    
    def execute_command(self, cmd: Dict[str, Any]):
        """Execute direct command."""
        cmd_type = cmd.get('type')
        
        if cmd_type == 'reset':
            self.sensor_data.clear()
            self.cycle_count = 0
            self.get_logger().info('Agent reset')
        elif cmd_type == 'status':
            status = {
                'connected': self.connected,
                'cycle': self.cycle_count,
                'sensors': list(self.sensor_data.keys())
            }
            status_msg = String()
            status_msg.data = json.dumps(status)
            self.status_pub.publish(status_msg)


def main(args=None):
    rclpy.init(args=args)
    node = MatrixNode()
    
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.shutdown()


if __name__ == '__main__':
    main()
