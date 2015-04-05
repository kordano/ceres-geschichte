#!/bin/bash
docker stop ceres-geschichte;
docker rm ceres-geschichte;
docker run -d -v /opt/data/ceres-geschichte/k-geschichte:/opt/data -v /opt/data/ceres-geschichte/k-log:/opt/log -v /opt/data/ceres-geschichte/k-benchmark:/opt/benchmark --name ceres-geschichte kordano/ceres-geschichte
