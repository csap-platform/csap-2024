#!/usr/bin/env python

import socket
# import re
import sys
import os

def check_server(address, port):
    #create a TCP socket
    s = socket.socket()
    print("Attempting to connect to %s on port %s" % (address, port))
    try:
        s.connect((address, port))
        print("Connected to %s on port %s" % (address, port))
        return True
    except socket.error as e:
        print("Connection to %s on port %s failed: %s" % (address, port, e))
        return False

if __name__ == '__main__':
    from optparse import OptionParser
    parser = OptionParser()
    
    # stream = os.popen('hostname')
    # currentHost = stream.read()
    # currentHost = os.environ["HOSTNAME"] ;
    currentHost = socket.gethostname() ;
    print('\n\n Host: %s\n\n ' % currentHost)

    parser.add_option("-a", "--address", dest="address", default='csap-dev01',
                      help="ADDRESS for server", metavar="ADDRESS")

    parser.add_option("-p", "--port", dest="port", type="int", default=8011,
                      help="PORT for server", metavar="PORT")

    (options, args) = parser.parse_args()
    print('options: %s, args: %s' % (options, args))
    check = check_server(options.address, options.port)
    print('check_server returned %s' % check)
    sys.exit(not check)