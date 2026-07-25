"""Launch file for Matrix ROS2 integration."""

from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node


def generate_launch_description():
    return LaunchDescription([
        DeclareLaunchArgument(
            'matrix_url',
            default_value='http://localhost:9091',
            description='Matrix core URL'
        ),
        DeclareLaunchArgument(
            'agent_id',
            default_value='ros2-agent',
            description='Agent ID for Matrix'
        ),
        DeclareLaunchArgument(
            'rate_hz',
            default_value='10.0',
            description='Control loop rate in Hz'
        ),

        Node(
            package='matrix_ros2',
            executable='sensor_fusion',
            name='sensor_fusion',
            output='screen'
        ),

        Node(
            package='matrix_ros2',
            executable='matrix_node',
            name='matrix_node',
            output='screen',
            parameters=[{
                'matrix_url': LaunchConfiguration('matrix_url'),
                'agent_id': LaunchConfiguration('agent_id'),
                'rate_hz': LaunchConfiguration('rate_hz'),
            }]
        ),
    ])
