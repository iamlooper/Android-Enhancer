#pragma once

#include <iostream>

// Enumeration for different types of log messages
enum class LogType {
    INFO,
    WARNING,
    ERROR
};

class Logger {
public:
    // Constructor to initialize log type and stream
    Logger(LogType type, std::ostream &logStream = std::cout);

    // Overload stream insertion operator for Logger
    template<typename T>
    Logger &operator<<(const T &value);

    // Overload stream insertion operator for manipulators
    Logger &operator<<(std::ostream &(*manip)(std::ostream &));

private:
    // Get current time as a string
    static std::string getTimestamp();

    // Convert LogType enum to corresponding string
    static std::string logTypeToString(LogType type);

    // Log type and stream member variables
    LogType logType;
    std::ostream &logStream;
};

// Template implementation
template<typename T>
Logger &Logger::operator<<(const T &value) {
    // Log values
    logStream << value;
    return *this;
}