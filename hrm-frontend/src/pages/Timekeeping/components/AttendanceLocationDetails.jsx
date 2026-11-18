import React from 'react';
import { Typography } from '@mui/material';
import { getLocationMapUrl } from '../../../utils/attendanceLocation.js';

export default function AttendanceLocationDetails({ location }) {
  const mapUrl = getLocationMapUrl(location);
  if (!mapUrl) return null;
  return (
    <Typography variant="caption" component="div" className="text-muted-foreground mt-0.5">
      <a href={mapUrl} target="_blank" rel="noopener noreferrer" className="text-primary underline">
        Vị trí: {location.latitude.toFixed(6)}, {location.longitude.toFixed(6)}
      </a>
      {Number.isFinite(location.locationAccuracy) && location.locationAccuracy >= 0 && (
        <span> · Sai số ±{Math.round(location.locationAccuracy)} m</span>
      )}
    </Typography>
  );
}
