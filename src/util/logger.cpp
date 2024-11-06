#include "logger.hpp"

#include <sstream>
#include <iomanip>
#include <ctime>

Logger::Logger(LogType type, std::ostream &logStream) : logType(type), logStream(logStream) {
    // Initialize log with timestamp and log type
    logStream << "[" << getTimestamp() << "] ";
    logStream << "[" << logTypeToString(type) << "] ";
}

Logger &Logger::operator<<(std::ostream &(*manip)(std::ostream &)) {
    // Log manipulators
    logStream << manip;
    return *this;
}

std::string Logger::getTimestamp() {
    std::time_t current_time = std::time(nullptr);
    std::stringstream date;
    date << std::put_time(std::localtime(&current_time), "%Y-%m-%d %H:%M:%S");
    return date.str();
}

std::string Logger::logTypeToString(LogType type) {
    switch (type) {
        case LogType::INFO:
            return "INFO";
        case LogType::WARNING:
            return "WARNING";
        case LogType::ERROR:
            return "ERROR";
        default:
            return "UNKNOWN";
    }
}