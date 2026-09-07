package com.orbitflow.task;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WatcherRepository extends JpaRepository<Watcher, Watcher.WatcherId> {
}
