# ============================
# 1) BUILD STAGE
# ============================
FROM gradle:8.7-jdk21 AS builder
WORKDIR /app

# Copy everything
COPY . .

# Build ứng dụng (tắt test để build nhanh)
RUN gradle clean bootJar -x test

# ============================
# 2) RUN STAGE
# ============================
FROM gcr.io/distroless/java21-debian12

WORKDIR /app

# Copy file jar từ builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose port (nếu bạn chạy port 8080)
EXPOSE 8080

# Chạy ứng dụng
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
