#ifndef JNIPOOL_POOL_H
#define JNIPOOL_POOL_H

#include <mutex>
#include <unordered_map>

template<typename Instance>
class Pool {
private:
    std::mutex mutex;
    std::unordered_map<uint64_t, Instance *> instances{};

public:
    ~Pool() {
        std::lock_guard <std::mutex> lock(mutex);
        for (auto &pair: instances) {
            delete pair.second;
        }
        instances.clear();
    }

    bool create(uint64_t id, const std::string &location) {
        std::lock_guard <std::mutex> lock(mutex);
        return instances.emplace(id, new Instance(location)).second;
    }

    Instance *acquire(uint64_t id) {
        std::lock_guard <std::mutex> lock(mutex);
        auto it = instances.find(id);
        return (it != instances.end()) ? it->second : nullptr;
    }

    bool release(uint64_t id) {
        std::lock_guard <std::mutex> lock(mutex);
        auto it = instances.find(id);
        if (it != instances.end()) {
            delete it->second;
            instances.erase(it);
            return true;
        }
        return false;
    }
};

#endif //JNIPOOL_POOL_H
