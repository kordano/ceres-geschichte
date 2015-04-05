#!/bin/bash
docker stop ceres-geschichte;
docker rm ceres-geschichte;
docker run -d -v /opt/data/ceres-geschichte/k-geschichte:/opt/data -v /opt/data/ceres-geschichte/k-log:/opt/log -v /opt/data/ceres-geschichte/k-benchmark:/opt/benchmark  -p 31744:31744 --name ceres-geschichte kordano/ceres-geschichte
