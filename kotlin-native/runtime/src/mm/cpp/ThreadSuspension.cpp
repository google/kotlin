/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "ThreadData.hpp"
#include "ThreadSuspension.hpp"

#include <condition_variable>
#include <thread>
#include <mutex>

#include "Logging.hpp"
#include "StackTrace.hpp"

namespace {

bool isSuspendedOrNative(kotlin::mm::ThreadData& thread) noexcept {
    auto& suspensionData = thread.suspensionData();
    return suspensionData.suspended() || suspensionData.state() == kotlin::ThreadState::kNative;
}

bool isSuspendedOrMarkingOrNative(kotlin::mm::ThreadData& thread) noexcept {
    auto& suspensionData = thread.suspensionData();
    return suspensionData.suspended() || suspensionData.marking() || suspensionData.state() == kotlin::ThreadState::kNative;
}

template<typename F>
bool allThreads(F predicate) noexcept {
    auto& threadRegistry = kotlin::mm::ThreadRegistry::Instance();
    auto* currentThread = (threadRegistry.IsCurrentThreadRegistered())
            ? threadRegistry.CurrentThreadData()
            : nullptr;
    kotlin::mm::ThreadRegistry::Iterable threads = kotlin::mm::ThreadRegistry::Instance().LockForIter();
    for (auto& thread : threads) {
        // Handle if suspension was initiated by the mutator thread.
        if (&thread == currentThread)
            continue;
        if (!predicate(thread)) {
            return false;
        }
    }
    return true;
}

void yield() noexcept {
    std::this_thread::yield();
}

THREAD_LOCAL_VARIABLE bool gSuspensionRequestedByCurrentThread = false;
[[clang::no_destroy]] std::mutex gSuspensionMutex;
[[clang::no_destroy]] std::condition_variable gSuspensionCondVar;
[[clang::no_destroy]] std::condition_variable gMarkingCondVar;

} // namespace

std::atomic<bool> kotlin::mm::internal::gSuspensionRequested = false;
std::atomic<bool> kotlin::mm::internal::gMarkingRequested = false;

NO_EXTERNAL_CALLS_CHECK void kotlin::mm::ThreadSuspensionData::suspendIfRequestedSlowPath() noexcept {
    if (IsThreadSuspensionRequested()) {
        auto threadId = konan::currentThreadId();
        auto suspendStartMs = konan::getTimeMicros();
        RuntimeLogDebug({kTagGC, kTagMM}, "Suspending thread %d", threadId);
        AutoReset scopedAssignMarking(&marking_, isMarkingRequested());
        if (marking_) {
            auto& threadRegistry = kotlin::mm::ThreadRegistry::Instance();
            auto* currentThread = threadRegistry.CurrentThreadData();
            currentThread->Publish();
            {
                std::unique_lock lock(gSuspensionMutex);
                gSuspensionCondVar.wait(lock, []() { return !isMarkingRequested(); });
            }
            RuntimeLogDebug({kTagGC, kTagMM}, "Marking in thread %d", threadId);
            currentThread->gc().Mark();
        }
        std::unique_lock lock(gSuspensionMutex);
        AutoReset scopedAssignSuspended(&suspended_, true);
        gMarkingCondVar.notify_all();
        gSuspensionCondVar.wait(lock, []() { return !IsThreadSuspensionRequested(); });
        auto suspendEndMs = konan::getTimeMicros();
        RuntimeLogDebug({kTagGC, kTagMM}, "Resuming thread %d after %" PRIu64 " microseconds of suspension",
                        threadId, suspendEndMs - suspendStartMs);
    }
}

NO_EXTERNAL_CALLS_CHECK bool kotlin::mm::RequestThreadsSuspension(bool markBeforeSuspending) noexcept {
    RuntimeAssert(gSuspensionRequestedByCurrentThread == false, "Current thread already suspended threads.");
    {
        std::unique_lock lock(gSuspensionMutex);
        bool actual = false;
        internal::gSuspensionRequested.compare_exchange_strong(actual, true);
        if (actual) {
            return false;
        }
        internal::gMarkingRequested = markBeforeSuspending;
    }
    gSuspensionRequestedByCurrentThread = true;

    return true;
}

void kotlin::mm::WaitForThreadsSuspension() noexcept {
    // Spin wating for threads to suspend. Ignore Native threads.
    while(!allThreads(isSuspendedOrNative)) {
        yield();
    }
}

void kotlin::mm::WaitForThreadsReadyToMark() noexcept {
    // Spin wating for threads to suspend. Ignore Native threads.
    while(!allThreads(isSuspendedOrMarkingOrNative)) {
        yield();
    }
}

void kotlin::mm::WaitForThreadsMarking() noexcept {
    RuntimeAssert(!isMarkingRequested(), "No new threads should be able to start marking while we wait");
    std::unique_lock lock(gSuspensionMutex);
    gMarkingCondVar.wait(lock, []() {
        return allThreads(isSuspendedOrNative);
    });
}

NO_INLINE void kotlin::mm::SuspendIfRequestedSlowPath() noexcept {
    mm::ThreadRegistry::Instance().CurrentThreadData()->suspensionData().suspendIfRequestedSlowPath();
}

ALWAYS_INLINE void kotlin::mm::SuspendIfRequested() noexcept {
    if (IsThreadSuspensionRequested()) {
        SuspendIfRequestedSlowPath();
    }
}

void kotlin::mm::RequestThreadsStartMarking() noexcept {
    {
        std::unique_lock lock(gSuspensionMutex);
        internal::gMarkingRequested = false;
    }
    gSuspensionCondVar.notify_all();
}

void kotlin::mm::ResumeThreads() noexcept {
    // From the std::condition_variable docs:
    // Even if the shared variable is atomic, it must be modified under
    // the mutex in order to correctly publish the modification to the waiting thread.
    // https://en.cppreference.com/w/cpp/thread/condition_variable
    {
        std::unique_lock lock(gSuspensionMutex);
        internal::gSuspensionRequested = false;
    }
    gSuspensionRequestedByCurrentThread = false;
    gSuspensionCondVar.notify_all();
}
