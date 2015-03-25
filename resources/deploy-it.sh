#!/bin/bash
docker stop ceres-geschichte;
docker rm ceres-geschichte;
docker run -d -v /home/konrad/data/ceres-geschichte:/opt/data --link mongodb:db --name ceres-geschichte kordano/ceres-geschichte
