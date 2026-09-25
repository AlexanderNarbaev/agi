"""
MATRIX Python SDK — pip install matrix-ai

Production-grade Python client for matrix-api-gateway.
"""
from setuptools import setup, find_packages

setup(
    name="matrix-ai",
    version="0.1.0-T08",
    description="Python SDK for the MATRIX Hybrid Neuro-Symbolic AI API",
    author="MATRIX Team",
    license="Apache-2.0",
    python_requires=">=3.10",
    packages=find_packages(where="src"),
    package_dir={"": "src"},
    install_requires=[
        "requests>=2.31",
        "pydantic>=2.5",
    ],
    classifiers=[
        "Programming Language :: Python :: 3",
        "License :: OSI Approved :: Apache Software License",
        "Operating System :: OS Independent",
    ],
)
