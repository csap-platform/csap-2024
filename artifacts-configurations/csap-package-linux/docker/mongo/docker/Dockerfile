
FROM mongo:4.4.4
#FROM mongo:4.2.12


RUN echo xxx

# change perm on local directory point so mongdb user has write access.
RUN chmod 777 /docker-entrypoint-initdb.d


COPY setup-1-update-event-schema.sh /docker-entrypoint-initdb.d/
RUN chmod 777 /docker-entrypoint-initdb.d/setup-1-update-event-schema.sh


COPY setup-2-event-schema.js /docker-entrypoint-initdb.d
RUN chmod 666 /docker-entrypoint-initdb.d/setup-2-event-schema.js

COPY setup-3-done.js /docker-entrypoint-initdb.d
RUN chmod 666 /docker-entrypoint-initdb.d/setup-3-done.js
