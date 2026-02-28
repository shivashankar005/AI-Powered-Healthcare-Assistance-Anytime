import React, { useState, useCallback, useEffect } from 'react';
import { useAuth } from '../context/useAuth';
import { doctorAPI } from '../services/api';
import { FaSync } from 'react-icons/fa';

import DoctorSidebar      from '../components/doctor/DoctorSidebar';
import DoctorOverview     from '../components/doctor/DoctorOverview';
import DoctorAppointments from '../components/doctor/DoctorAppointments';
import DoctorPatients     from '../components/doctor/DoctorPatients';
import DoctorSoapNote     from '../components/doctor/DoctorSoapNote';
import DoctorProfile      from '../components/doctor/DoctorProfile';

const SECTION_LABELS = {
  overview:     'Dashboard Overview',
  appointments: 'Appointments',
  patients:     'My Patients',
  'soap-note':  'SOAP Note Generator',
  profile:      'My Profile',
};

const DoctorDashboard = ({ initialSection = 'overview' }) => {
  const { user } = useAuth();
  const [activeSection, setActiveSection] = useState(initialSection);
  const [refreshKey, setRefreshKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState({
    stats:        null,
    appointments: [],
    patients:     [],
  });

  const fetchAll = useCallback(async () => {
    setLoading(true);
    try {
      const [statsRes, appointmentsRes, patientsRes] = await Promise.allSettled([
        doctorAPI.getStats(),
        doctorAPI.getAppointments(),
        doctorAPI.getPatients(),
      ]);
      setData({
        stats:        statsRes.status        === 'fulfilled' ? statsRes.value.data?.data                : null,
        appointments: appointmentsRes.status === 'fulfilled' ? (appointmentsRes.value.data?.data ?? [])  : [],
        patients:     patientsRes.status     === 'fulfilled' ? (patientsRes.value.data?.data     ?? [])  : [],
      });
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchAll(); }, [fetchAll, refreshKey]);

  const handleRefresh = () => setRefreshKey(k => k + 1);

  const renderSection = () => {
    switch (activeSection) {
      case 'overview':     return <DoctorOverview stats={data.stats} appointments={data.appointments} onNavigate={setActiveSection} />;
      case 'appointments': return <DoctorAppointments appointments={data.appointments} onRefresh={handleRefresh} />;
      case 'patients':     return <DoctorPatients patients={data.patients} />;
      case 'soap-note':    return <DoctorSoapNote patients={data.patients} />;
      case 'profile':      return <DoctorProfile user={user} stats={data.stats} />;
      default:             return null;
    }
  };

  return (
    <div className="h-screen flex overflow-hidden bg-gray-50">
      <DoctorSidebar activeSection={activeSection} onNavigate={setActiveSection} />

      <div className="flex-1 flex flex-col overflow-hidden">
        <header className="bg-white border-b border-gray-100 px-6 py-4 flex items-center justify-between shadow-sm flex-shrink-0">
          <div>
            <h1 className="text-lg font-bold text-gray-800">{SECTION_LABELS[activeSection]}</h1>
            <p className="text-xs text-gray-400">Welcome back, Dr. {user?.fullName || user?.username}</p>
          </div>
          <button
            onClick={handleRefresh}
            disabled={loading}
            title="Refresh data"
            className="p-2 rounded-xl border border-gray-200 text-gray-400 hover:text-blue-600 hover:bg-blue-50 transition-colors disabled:opacity-50"
          >
            <FaSync className={loading ? 'animate-spin' : ''} />
          </button>
        </header>

        <main className="flex-1 overflow-y-auto p-6">
          {loading ? (
            <div className="flex items-center justify-center h-48">
              <div className="w-10 h-10 border-4 border-blue-100 border-t-blue-500 rounded-full animate-spin" />
            </div>
          ) : (
            renderSection()
          )}
        </main>
      </div>
    </div>
  );
};

export default DoctorDashboard;
