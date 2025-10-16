# Documentation Index
## IPS Data Acquisition App - Complete Guide

**Version**: 2.1 | **Last Updated**: October 2025

---

## 📚 Documentation Overview

This project has comprehensive documentation covering mobile app, backend API, architecture, and setup. Use this index to find what you need quickly.

---

## 🎯 Start Here (By Role)

### For Backend Developers

**Start with these (in order)**:

1. **`API_QUICK_REFERENCE.md`** (5 min)
   - Quick overview of 6 API endpoints
   - Sample requests/responses
   - Database schema summary

2. **`API_DOCUMENTATION.md`** (30 min)
   - Complete API specification
   - All request/response details
   - Error handling
   - Testing examples

3. **`SENSOR_DATA_SPECIFICATION.md`** (20 min)
   - All 61 sensor parameters explained
   - Units, ranges, descriptions
   - Database schema for IMU data

4. **`SYSTEM_ARCHITECTURE.md`** (20 min)
   - Overall system design
   - Mobile app behavior
   - Queue-based sync explained
   - Performance considerations

### For Mobile Developers

**Start with these**:

1. **`PROJECT_SUMMARY.md`**
   - Complete project overview
   - Features and implementation

2. **`SETUP_GUIDE.md`**
   - Development environment setup
   - Build instructions
   - Testing procedures

3. **`SYSTEM_ARCHITECTURE.md`**
   - Mobile app architecture
   - MVVM pattern
   - Background services

### For Project Managers

**Start with these**:

1. **`FINAL_UPDATE_SUMMARY.md`**
   - Executive summary
   - What was built
   - Status and readiness

2. **`README.md`**
   - Project overview
   - Key features
   - Quick start

---

## 📖 Complete Documentation List

### Core Documentation

| Document | Purpose | Audience | Read Time |
|----------|---------|----------|-----------|
| **`README.md`** | Project overview and features | Everyone | 10 min |
| **`SETUP_GUIDE.md`** | Development setup instructions | Developers | 15 min |
| **`PROJECT_SUMMARY.md`** | Complete project details | Everyone | 20 min |
| **`FINAL_UPDATE_SUMMARY.md`** | Executive summary | PM/Stakeholders | 15 min |

### API Documentation

| Document | Purpose | Audience | Read Time |
|----------|---------|----------|-----------|
| **`API_DOCUMENTATION.md`** | Complete API specification | Backend Devs | 30 min |
| **`API_QUICK_REFERENCE.md`** | Quick API reference | Backend Devs | 5 min |
| **`SENSOR_DATA_SPECIFICATION.md`** | All 61 sensor parameters | Backend/ML Team | 20 min |

### Architecture Documentation

| Document | Purpose | Audience | Read Time |
|----------|---------|----------|-----------|
| **`SYSTEM_ARCHITECTURE.md`** | Overall system design | All Developers | 20 min |
| **`QUEUE_ARCHITECTURE_SUMMARY.md`** | Queue-based sync explained | Developers | 10 min |

### Reference Documentation

| Document | Purpose | Audience | Read Time |
|----------|---------|----------|-----------|
| **`DOCUMENTATION_INDEX.md`** | This file - documentation guide | Everyone | 5 min |
| **`CHANGES_SUMMARY.md`** | Summary of all changes made | PM/Developers | 10 min |

---

## 🔍 Documentation by Topic

### Want to Understand...

#### The API Endpoints?
→ Start with `API_QUICK_REFERENCE.md`  
→ Then read `API_DOCUMENTATION.md` for details

#### Sensor Data Collection?
→ Read `SENSOR_DATA_SPECIFICATION.md`  
→ See all 61 parameters explained

#### Queue-Based Sync?
→ Read `QUEUE_ARCHITECTURE_SUMMARY.md`  
→ Understand offline-first design

#### Overall Architecture?
→ Read `SYSTEM_ARCHITECTURE.md`  
→ See mobile + backend design

#### How to Setup Development?
→ Read `SETUP_GUIDE.md`  
→ Step-by-step instructions

#### What Was Built?
→ Read `FINAL_UPDATE_SUMMARY.md`  
→ Executive summary

---

## 📱 Mobile App Documentation

### Code Structure
```
app/src/main/java/com/ips/dataacquisition/
├── data/
│   ├── local/         # Room database, DAOs
│   ├── model/         # Data models (Session, IMUData, etc)
│   ├── remote/        # Retrofit API, DTOs
│   └── repository/    # Data repositories
├── service/
│   ├── IMUDataService.kt      # Sensor collection
│   └── DataSyncService.kt     # Queue processor
├── ui/
│   ├── screen/        # Compose screens
│   ├── theme/         # Material Design theme
│   └── viewmodel/     # ViewModels
├── util/              # Utility classes
└── MainActivity.kt    # Main entry point
```

### Key Files Explained

| File | Purpose |
|------|---------|
| `IMUData.kt` | Data model with 61 sensor parameters |
| `IMUDataService.kt` | Collects data from 21 sensors |
| `DataSyncService.kt` | Processes queue, syncs to backend |
| `SessionRepository.kt` | Queue-first logic for button presses |
| `HomeViewModel.kt` | Button flow business logic |

---

## 🌐 Backend Documentation

### API Endpoints Summary

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/sessions/create` | POST | Create new session |
| `/sessions/close` | POST | End session |
| `/button-presses` | POST | Record waypoint |
| `/imu-data/upload` | POST | Upload sensor batch |
| `/sessions` | GET | List sessions |
| `/bonuses` | GET | Get bonuses |

**Total**: 6 endpoints

### Database Tables

| Table | Columns | Purpose |
|-------|---------|---------|
| `sessions` | 8 | Session metadata |
| `button_presses` | 6 | Waypoint queue |
| `imu_data` | 66 | Sensor data (61 params + meta) |
| `bonuses` | 6 | Daily bonuses |

---

## 🎓 Learning Path

### Day 1: Understanding the System

1. Read `README.md` (10 min)
2. Read `FINAL_UPDATE_SUMMARY.md` (15 min)
3. Skim `SYSTEM_ARCHITECTURE.md` (20 min)

**Goal**: Understand what the system does and why

### Day 2: Backend Development

1. Read `API_QUICK_REFERENCE.md` (5 min)
2. Read `API_DOCUMENTATION.md` (30 min)
3. Read `SENSOR_DATA_SPECIFICATION.md` (20 min)
4. Start implementing endpoints

**Goal**: Implement backend API

### Day 3: Mobile Development

1. Read `SETUP_GUIDE.md` (15 min)
2. Read `QUEUE_ARCHITECTURE_SUMMARY.md` (10 min)
3. Review code structure
4. Build and test app

**Goal**: Run and understand mobile app

### Week 2: Integration & Testing

1. Mobile team updates BASE_URL
2. Test all endpoints
3. Verify offline mode
4. Performance testing

**Goal**: Complete integration

---

## 🔗 Quick Links

### For Different Scenarios

**"I need to implement the backend API"**
→ `API_DOCUMENTATION.md` + `API_QUICK_REFERENCE.md`

**"I need to understand the sensor data"**
→ `SENSOR_DATA_SPECIFICATION.md`

**"I need to set up the development environment"**
→ `SETUP_GUIDE.md`

**"I need to understand how offline sync works"**
→ `QUEUE_ARCHITECTURE_SUMMARY.md`

**"I need to present this to stakeholders"**
→ `FINAL_UPDATE_SUMMARY.md`

**"I need to understand the overall architecture"**
→ `SYSTEM_ARCHITECTURE.md`

**"I need a quick API reference"**
→ `API_QUICK_REFERENCE.md`

---

## 📊 Documentation Statistics

| Category | Files | Total Pages (est) |
|----------|-------|-------------------|
| Core | 4 | ~40 |
| API | 3 | ~50 |
| Architecture | 2 | ~30 |
| Reference | 2 | ~15 |
| **Total** | **11** | **~135 pages** |

---

## ✅ Documentation Checklist

Use this to track your reading progress:

### Backend Developer
- [ ] Read API_QUICK_REFERENCE.md
- [ ] Read API_DOCUMENTATION.md
- [ ] Read SENSOR_DATA_SPECIFICATION.md
- [ ] Read SYSTEM_ARCHITECTURE.md
- [ ] Implement database schema
- [ ] Implement 6 API endpoints
- [ ] Test with cURL
- [ ] Deploy to staging

### Mobile Developer
- [ ] Read SETUP_GUIDE.md
- [ ] Read PROJECT_SUMMARY.md
- [ ] Read SYSTEM_ARCHITECTURE.md
- [ ] Build app successfully
- [ ] Test on device
- [ ] Update BASE_URL
- [ ] Test integration

### Project Manager
- [ ] Read FINAL_UPDATE_SUMMARY.md
- [ ] Read README.md
- [ ] Understand deliverables
- [ ] Review timeline
- [ ] Coordinate teams

---

## 💡 Documentation Tips

### For Backend Developers

1. **Start small**: Read Quick Reference first
2. **Use examples**: All curl commands provided
3. **Test as you go**: Build endpoints incrementally
4. **Ask questions**: Unclear? Check detailed docs

### For Mobile Developers

1. **Code is documented**: Read inline comments
2. **Follow setup guide**: Step-by-step instructions
3. **Understand queue**: Key to the architecture
4. **Test offline mode**: Critical feature

### For Everyone

1. **Use Ctrl+F**: Search within documents
2. **Check cross-references**: Documents link to each other
3. **Start with summaries**: Then dive deeper
4. **Real code**: Documentation matches implementation

---

## 🆘 Need Help?

### Can't Find What You Need?

1. **Search this index** for keywords
2. **Check related documentation** (cross-references)
3. **Review code directly** (well-commented)
4. **Contact the team** (details in project files)

### Documentation Feedback

If you find:
- Missing information
- Unclear explanations
- Errors or inconsistencies

Please report to the project team.

---

## 📅 Last Updated

**Date**: October 2025  
**Version**: 2.1  
**Status**: Current

**Documentation reflects**:
- Latest code implementation
- 61 sensor parameters
- Queue-based architecture
- 6 API endpoints
- Production-ready system

---

## 🎯 Quick Decision Guide

**"Which doc should I read?"**

```
If you want to:
├─ Implement backend
│  └─→ API_DOCUMENTATION.md
│
├─ Understand sensors
│  └─→ SENSOR_DATA_SPECIFICATION.md
│
├─ Setup development
│  └─→ SETUP_GUIDE.md
│
├─ Understand architecture
│  └─→ SYSTEM_ARCHITECTURE.md
│
├─ Quick API lookup
│  └─→ API_QUICK_REFERENCE.md
│
├─ Project overview
│  └─→ README.md
│
└─ Executive summary
   └─→ FINAL_UPDATE_SUMMARY.md
```

---

**Happy Reading! 📖**

This documentation is comprehensive, current, and based on actual code implementation. Everything you need to understand, build, and deploy the system is here.

---

**Index Version**: 2.1  
**Coverage**: 100% of system  
**Status**: ✅ **COMPLETE**

