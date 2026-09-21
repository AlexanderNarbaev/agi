#!/usr/bin/env python3
"""
setup.py — ROS2 package setup for matrix_ros2.
"""

from setuptools import setup, find_packages

package_name = 'matrix_ros2'

setup(
    name=package_name,
    version='3.58.0',
    packages=find_packages(),
    data_files=[
        ('share/ament_index/resource_index/packages', ['resource/' + package_name]),
        ('share/' + package_name, ['package.xml']),
        ('share/' + package_name + '/launch', ['launch/matrix_robot.launch.py']),
    ],
    install_requires=['setuptools', 'requests'],
    zip_safe=True,
    maintainer='Alexander Narbaev',
    maintainer_email='alexander@narbaev.io',
    description='ROS2 bridge for M.A.T.R.I.X. cognitive architecture',
    license='AGPL-3.0',
    tests_require=['pytest'],
    entry_points={
        'console_scripts': [
            'matrix_node = matrix_node:main',
            'sensor_fusion = sensor_fusion:main',
        ],
    },
)
