import React from 'react';
import {
  FaCalendarAlt, FaUsers, FaClock, FaCheckCircle,
  FaTimesCircle, FaClipboardList, FaArrowRight,
} from 'react-icons/fa';

const formatDate = (v) =>
  v ? new Date(v).toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';

const StatCard = ({ icon: Icon, label, value, color, border }) => (
  <div className={`bg-white rounded-xl p-5 border ${border} shadow-sm hover:shadow-md transition-all duration-200 hover:-translate-y-0.5`}>
    <div className={`inline-flex items-center justify-center w-11 h-11 rounded-xl ${color} mb-3`}>
      <Icon className="text-white text-lg" />
    </div>
    <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-1">{label}</p>
    <p className="text-3xl font-bold text-gray-800">{value ?? 0}</p>
  </div>
);

const DoctorOverview = ({ stats, appointments, onNavigate }) => {
  const recent = (appointments || []).slice(0, 5);
  const today = new Date().toDateString();
  const todayAppts = (appointments || []).filter(
    a => new Date(a.appointmentDate).toDateString() === today
  );

  const STATUS_STYLES = {
    PENDING:   'bg-yellow-100 text-yellow-700',
    CONFIRMED: 'bg-blue-100 text-blue-700',
    COMPLETED: 'bg-green-100 text-green-700',
    CANCELLED: 'bg-gray-100 text-gray-500',
  };

  return (
    <div className="space-y-6">
      {/* Stats Row */}
      <div className="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4">
        <StatCard icon={FaCalendarAlt} label="Total Appts"  value={stats?.totalAppointments} color="bg-gradient-to-br from-blue-500 to-blue-600"    border="border-blue-100" />
        <StatCard icon={FaUsers}       label="Patients"     value={stats?.totalPatients}      color="bg-gradient-to-br from-indigo-500 to-indigo-600"  border="border-indigo-100" />
        <StatCard icon={FaClock}       label="Pending"      value={stats?.pending}            color="bg-gradient-to-br from-yellow-500 to-amber-500"    border="border-yellow-100" />
        <StatCard icon={FaClipboardList} label="Confirmed"  value={stats?.confirmed}          color="bg-gradient-to-br from-sky-500 to-sky-600"         border="border-sky-100" />
        <StatCard icon={FaCheckCircle} label="Completed"    value={stats?.completed}          color="bg-gradient-to-br from-emerald-500 to-emerald-600" border="border-emerald-100" />
        <StatCard icon={FaTimesCircle} label="Cancelled"    value={stats?.cancelled}          color="bg-gradient-to-br from-rose-400 to-rose-500"       border="border-rose-100" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Today's appointments */}
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-800 flex items-center gap-2">
              <FaCalendarAlt className="text-blue-500" /> Today's Appointments
            </h2>
            <span className="text-xs bg-blue-50 text-blue-600 font-semibold px-2 py-1 rounded-full">{todayAppts.length}</span>
          </div>
          {todayAppts.length === 0 ? (
            <p className="text-gray-400 text-sm text-center py-6">No appointments today</p>
          ) : (
            <ul className="space-y-3">
              {todayAppts.map(a => (
                <li key={a.id} className="flex items-center justify-between p-3 rounded-lg bg-gray-50 border border-gray-100">
                  <div>
                    <p className="text-sm font-semibold text-gray-800">{a.patient?.fullName || a.patient?.username || `Patient #${a.patient?.id}`}</p>
                    <p className="text-xs text-gray-400">{formatDate(a.appointmentDate)}</p>
                    {a.reason && <p className="text-xs text-gray-500 mt-0.5 truncate max-w-[200px]">{a.reason}</p>}
                  </div>
                  <span className={`text-xs font-semibold px-2.5 py-1 rounded-full ${STATUS_STYLES[a.status] || 'bg-gray-100 text-gray-500'}`}>
                    {a.status}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Recent appointments */}
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-800 flex items-center gap-2">
              <FaClipboardList className="text-indigo-500" /> Recent Appointments
            </h2>
            <button
              onClick={() => onNavigate('appointments')}
              className="text-xs text-blue-600 hover:underline flex items-center gap-1"
            >
              View all <FaArrowRight className="text-[10px]" />
            </button>
          </div>
          {recent.length === 0 ? (
            <p className="text-gray-400 text-sm text-center py-6">No appointments yet</p>
          ) : (
            <ul className="space-y-3">
              {recent.map(a => (
                <li key={a.id} className="flex items-center justify-between p-3 rounded-lg bg-gray-50 border border-gray-100">
                  <div>
                    <p className="text-sm font-semibold text-gray-800">{a.patient?.fullName || a.patient?.username || `Patient #${a.patient?.id}`}</p>
                    <p className="text-xs text-gray-400">{formatDate(a.appointmentDate)}</p>
                  </div>
                  <span className={`text-xs font-semibold px-2.5 py-1 rounded-full ${STATUS_STYLES[a.status] || 'bg-gray-100 text-gray-500'}`}>
                    {a.status}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
};

export default DoctorOverview;
