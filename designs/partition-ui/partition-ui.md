# Partition/region selection UI

## Overview
Users want the JetBrains toolkit to support other regions outside the classic partition (i.e. China and GovCloud regions).

When a user wants to switch to a different partition, they will need to change their region in addition to their credentials.

This is a quick, point-in-time document with several directions that we can go. Any decisions made here are not one-way doors and we can adjust the experience as necessary
if there is any user feedback.

## Mockups
Mockups below assumes a user in a fresh project and has not selected credentials yet.
The left view represents the credential/region selector in the initial state and the right view represents the result after selecting a region

---

1. Always show partition and filter the view based on the selected partition (currently impl'd in PR#1596)
![1]

1. Abstract away the idea of a partition (don't show users the partitions)
![2]

1. Only show partitions after a partition has been selected (either implicitly by selecting the region or directly selecting a partition)
![3]

1. Same as above, but partition is a submenu
![4]

1. Expose regions as a nested submenu of the partition submenu
![5]

 
[1]: img/1-always-show-partition.png
[2]: img/2-no-partitions.png
[3]: img/3-show-partition-after-select.png
[4]: img/4-show-partition-after-select-2.png
[5]: img/5-nested-submenu.png
