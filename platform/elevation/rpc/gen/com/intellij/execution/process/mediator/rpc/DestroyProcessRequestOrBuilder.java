// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: processMediator.proto

package com.intellij.execution.process.mediator.rpc;

public interface DestroyProcessRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:intellij.process.mediator.rpc.DestroyProcessRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>uint64 pid = 1;</code>
   * @return The pid.
   */
  long getPid();

  /**
   * <code>bool force = 2;</code>
   * @return The force.
   */
  boolean getForce();
}
